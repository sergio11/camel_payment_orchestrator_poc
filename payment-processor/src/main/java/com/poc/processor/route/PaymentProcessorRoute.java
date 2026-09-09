package com.poc.processor.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.processor.ContentBasedRouterBean;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class PaymentProcessorRoute extends RouteBuilder {

    // POC reliability: canonical Kafka topics for retry (transient, re-drivable) vs dead-letter (poison, terminal).
    // A4 will consume payments.events.retry; poison never goes to retry to avoid infinite loops.
    public static final String RETRY_TOPIC_URI = "kafka:{{kafka.topic.payments.retry}}";
    public static final String DEAD_LETTER_TOPIC_URI = "kafka:{{kafka.topic.dead.letter}}";
    public static final String DIRECT_RETRY_HANDLER = "direct:retry-handler";
    public static final String DIRECT_POISON_DLQ = "direct:poison-dlq";
    public static final String DIRECT_DLQ_HANDLER = "direct:dlq-handler";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ContentBasedRouterBean contentBasedRouterBean;

    public static void restorePaymentIdFromKafkaKey(org.apache.camel.Exchange exchange) {
        if (exchange.getMessage().getHeader("OriginalPaymentId") == null) {
            Object key = exchange.getMessage().getHeader("kafka.KEY");
            if (key != null) {
                exchange.getMessage().setHeader("OriginalPaymentId", key.toString());
            }
        }
    }

    public static void validatePaymentMessage(PaymentMessage msg) {
        if (msg.paymentId() == null || msg.amount() == null || msg.currency() == null
            || msg.customerId() == null || msg.paymentMethod() == null) {
            throw new IllegalArgumentException(
                "Invalid payment fields: required fields missing for payment " + msg.paymentId());
        }
    }

    @Override
    public void configure() {
        // Transient failures: 3 redeliveries with exponential back-off, then retry topic (re-drivable, A4).
        errorHandler(deadLetterChannel(DIRECT_RETRY_HANDLER)
            .useOriginalMessage()
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .logRetryAttempted(true)
            .logExhausted(true));

        // Poison messages (bad JSON, invalid fields): no redelivery, straight to dead-letter.
        // Must be declared before routes; handled(true) stops infinite reprocessing.
        onException(JsonProcessingException.class, IllegalArgumentException.class)
            .handled(true)
            .maximumRedeliveries(0)
            .logExhausted(true)
            .log("Poison message, routing to DLQ without retry: ${exception.message}")
            .to(DIRECT_POISON_DLQ);

        var paymentJson = new org.apache.camel.component.jackson.JacksonDataFormat(objectMapper, PaymentMessage.class);

        // POC: manual commit (autoCommitEnable=false) for at-least-once.
        // Camel commits the offset only after the route completes; failures are redelivered, poison goes to DLQ.
        from("kafka:{{kafka.topic.payments.received}}?groupId=payment-processor-group&autoCommitEnable=false&autoOffsetReset=earliest")
            .routeId("payment-processor")
            .autoStartup("{{camel.route.payment-processor.auto-startup:true}}")
            .unmarshal(paymentJson)
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                exchange.getIn().setHeader("OriginalPaymentId", msg.paymentId());
                exchange.getIn().setHeader("OriginalEventId", msg.eventId());
                validatePaymentMessage(msg);
            })
            .log("Received payment: ${header.OriginalPaymentId}")
            .wireTap("direct:audit-pipeline")
            .process("paymentEnrichProcessor")
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                String target = contentBasedRouterBean.routeToFraudCheck(msg.amount(), msg.paymentMethod(), msg.country());
                exchange.getIn().setHeader("FraudRouteTarget", target);
            })
            .log("Fraud route target: ${header.FraudRouteTarget} for ${body.paymentId}")
            .choice()
                .when(header("FraudRouteTarget").isEqualTo(ContentBasedRouterBean.DESTINATION_FRAUD_REVIEW))
                    .log("High amount or WALLET payment, routing to fraud review: ${body.paymentId}")
                    .to(ContentBasedRouterBean.DESTINATION_FRAUD_REVIEW)
                .otherwise()
                    .log("Standard payment, routing to fraud check: ${body.paymentId}")
                    .to(ContentBasedRouterBean.DESTINATION_FRAUD_CHECK)
            .end()
            .log("Payment processed: ${header.OriginalPaymentId} -> ${header.CamelFraudAction}");

        from(ContentBasedRouterBean.DESTINATION_FRAUD_REVIEW)
            .routeId("fraud-review-high-value")
            .process("fraudEvaluationProcessor")
            .choice()
                .when(simple("${header.CamelFraudAction} == 'REJECT'"))
                    .log("Fraud REJECT for high-value payment: ${body.paymentId}")
                    .to("direct:fraud-reject")
                .when(simple("${header.CamelFraudAction} == 'REVIEW'"))
                    .log("Fraud REVIEW for high-value payment: ${body.paymentId}")
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .log("Fraud APPROVE for high-value payment: ${body.paymentId}")
                    .to("direct:provider-selection")
            .end();

        from(ContentBasedRouterBean.DESTINATION_FRAUD_CHECK)
            .routeId("fraud-check-standard")
            .process("fraudEvaluationProcessor")
            .choice()
                .when(simple("${header.CamelFraudAction} == 'REJECT'"))
                    .log("Fraud REJECT: ${body.paymentId}")
                    .to("direct:fraud-reject")
                .when(simple("${header.CamelFraudAction} == 'REVIEW'"))
                    .log("Fraud REVIEW: ${body.paymentId}")
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .log("Fraud APPROVE: ${body.paymentId}")
                    .to("direct:provider-selection")
            .end();

        // Transient-exhausted handler: re-drivable retry topic (consumed by A4 reviewer, not auto-replayed).
        from(DIRECT_RETRY_HANDLER)
            .routeId("retry-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Exhausted retries for payment ${header.OriginalPaymentId}: ${exception.message}")
            .choice()
                .when(body().isInstanceOf(PaymentMessage.class))
                    .marshal(paymentJson)
            .end()
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(RETRY_TOPIC_URI)
            .log("Published to retry topic");

        // Poison handler: terminal dead-letter, no retry.
        from(DIRECT_POISON_DLQ)
            .routeId("poison-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Poison payment ${header.OriginalPaymentId}: ${exception.message}")
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(DEAD_LETTER_TOPIC_URI)
            .log("Published poison to dead letter queue");

        // Legacy alias kept for backward compatibility (other routes/tests may reference it).
        from(DIRECT_DLQ_HANDLER)
            .routeId("error-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Error processing payment ${header.OriginalPaymentId}: ${exception.message}")
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(DEAD_LETTER_TOPIC_URI)
            .log("Published to dead letter queue");
    }
}
