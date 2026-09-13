package com.poc.processor.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.application.FraudRoutingService;
import com.poc.processor.application.FraudRoutingService.FraudRoutingDecision;
import com.poc.processor.route.CamelRouteConstants;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.application.PaymentProcessingService;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class PaymentProcessorRoute extends RouteBuilder {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FraudRoutingService fraudRoutingService;

    @Inject
    EnrichPaymentUseCase enrichPaymentUseCase;

    @Inject
    EvaluateFraudUseCase evaluateFraudUseCase;

    public static void restorePaymentIdFromKafkaKey(org.apache.camel.Exchange exchange) {
        if (exchange.getMessage().getHeader("OriginalPaymentId") == null) {
            Object key = exchange.getMessage().getHeader("kafka.KEY");
            if (key != null) {
                exchange.getMessage().setHeader("OriginalPaymentId", key.toString());
            }
        }
    }

    @Override
    public void configure() {
        errorHandler(deadLetterChannel(CamelRouteConstants.DIRECT_RETRY_HANDLER)
            .useOriginalMessage()
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .logRetryAttempted(true)
            .logExhausted(true));

        onException(JsonProcessingException.class, com.poc.processor.domain.exception.PaymentProcessingException.class)
            .handled(true)
            .maximumRedeliveries(0)
            .logExhausted(true)
            .log("Poison message, routing to DLQ without retry: ${exception.message}")
            .to(CamelRouteConstants.DIRECT_POISON_DLQ);

        var paymentJson = new org.apache.camel.component.jackson.JacksonDataFormat(objectMapper, PaymentMessage.class);

        from("kafka:{{kafka.topic.payments.received}}?groupId=payment-processor-group&autoCommitEnable=false&autoOffsetReset=earliest")
            .routeId("payment-processor")
            .autoStartup("{{camel.route.payment-processor.auto-startup:true}}")
            .unmarshal(paymentJson)
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                exchange.getIn().setHeader("OriginalPaymentId", msg.paymentId());
                exchange.getIn().setHeader("OriginalEventId", msg.eventId());
                PaymentProcessingService.validatePaymentMessage(msg);
            })
            .log("Received payment: ${header.OriginalPaymentId}")
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                PaymentMessage enriched = enrichPaymentUseCase.enrich(msg);
                exchange.getIn().setBody(enriched);
            })
            .wireTap("direct:audit-pipeline")
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                exchange.getIn().setHeader("OriginalPaymentMessage", msg);

                FraudEvaluation evaluation = evaluateFraudUseCase.evaluate(msg);
                exchange.setProperty("FraudEvaluation", evaluation);

                FraudRoutingDecision decision = fraudRoutingService.route(msg, evaluation);
                exchange.getIn().setHeader("CamelFraudAction", decision.action().name());
                exchange.getIn().setHeader("CamelRiskScore", decision.evaluation().riskScore());
                exchange.getIn().setHeader("FraudRouteTarget", decision.routeTarget());
            })
            .log("Fraud route target: ${header.FraudRouteTarget} for ${body.paymentId}")
            .choice()
                .when(header("FraudRouteTarget").isEqualTo(CamelRouteConstants.DIRECT_FRAUD_REVIEW))
                    .log("High amount or WALLET payment, routing to fraud review: ${body.paymentId}")
                    .to(CamelRouteConstants.DIRECT_FRAUD_REVIEW)
                .otherwise()
                    .log("Standard payment, routing to fraud check: ${body.paymentId}")
                    .to(CamelRouteConstants.DIRECT_FRAUD_CHECK)
            .end()
            .log("Payment processed: ${header.OriginalPaymentId} -> ${header.CamelFraudAction}");

        from(CamelRouteConstants.DIRECT_FRAUD_REVIEW)
            .routeId("fraud-review-high-value")
            .log("Fraud evaluation already done for high-value payment: ${body.paymentId}")
            .choice()
                .when(header("CamelFraudAction").isEqualTo(FraudAction.REJECT.name()))
                    .log("Fraud REJECT for high-value payment: ${body.paymentId}")
                    .to("direct:fraud-reject")
                .when(header("CamelFraudAction").isEqualTo(FraudAction.REVIEW.name()))
                    .log("Fraud REVIEW for high-value payment: ${body.paymentId}")
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .log("Fraud APPROVE for high-value payment: ${body.paymentId}")
                    .to("direct:provider-selection")
            .end();

        from(CamelRouteConstants.DIRECT_FRAUD_CHECK)
            .routeId("fraud-check-standard")
            .log("Fraud evaluation already done for standard payment: ${body.paymentId}")
            .choice()
                .when(header("CamelFraudAction").isEqualTo(FraudAction.REJECT.name()))
                    .log("Fraud REJECT: ${body.paymentId}")
                    .to("direct:fraud-reject")
                .when(header("CamelFraudAction").isEqualTo(FraudAction.REVIEW.name()))
                    .log("Fraud REVIEW: ${body.paymentId}")
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .log("Fraud APPROVE: ${body.paymentId}")
                    .to("direct:provider-selection")
            .end();

        from(CamelRouteConstants.DIRECT_RETRY_HANDLER)
            .routeId("retry-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Exhausted retries for payment ${header.OriginalPaymentId}: ${exception.message}")
            .choice()
                .when(body().isInstanceOf(PaymentMessage.class))
                    .marshal(paymentJson)
            .end()
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(CamelRouteConstants.RETRY_TOPIC_URI)
            .log("Published to retry topic");

        from(CamelRouteConstants.DIRECT_POISON_DLQ)
            .routeId("poison-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Poison payment ${header.OriginalPaymentId}: ${exception.message}")
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(CamelRouteConstants.DEAD_LETTER_TOPIC_URI)
            .log("Published poison to dead letter queue");

        from(CamelRouteConstants.DIRECT_DLQ_HANDLER)
            .routeId("error-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Error processing payment ${header.OriginalPaymentId}: ${exception.message}")
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(CamelRouteConstants.DEAD_LETTER_TOPIC_URI)
            .log("Published to dead letter queue");

        from("kafka:{{kafka.topic.payments.retry}}?groupId=payment-processor-retry-group&autoCommitEnable=false&autoOffsetReset=earliest")
            .routeId("retry-consumer")
            .autoStartup("{{camel.route.retry-consumer.auto-startup:true}}")
            .log("Retrying payment from retry topic: ${header.kafka.KEY}")
            .process(exchange -> {
                Integer retryCount = exchange.getIn().getHeader("retryCount", 0, Integer.class);
                exchange.getIn().setHeader("retryCount", retryCount + 1);
            })
            .choice()
                .when(header("retryCount").isLessThan(3))
                    .log("Retry attempt ${header.retryCount} for ${header.kafka.KEY}")
                    .to("kafka:{{kafka.topic.payments.received}}")
                .otherwise()
                    .log("Max retries exceeded for ${header.kafka.KEY}, sending to DLQ")
                    .setHeader("kafka.KEY", header("kafka.KEY"))
                    .to("kafka:{{kafka.topic.dead-letter}}")
            .end();
    }
}
