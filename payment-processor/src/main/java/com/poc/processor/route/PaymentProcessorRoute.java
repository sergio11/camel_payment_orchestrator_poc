package com.poc.processor.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.application.FraudRoutingService;
import com.poc.processor.application.FraudRoutingService.FraudRoutingDecision;
import com.poc.processor.processor.ContentBasedRouterBean;
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

    public static final String RETRY_TOPIC_URI = "kafka:{{kafka.topic.payments.retry}}";
    public static final String DEAD_LETTER_TOPIC_URI = "kafka:{{kafka.topic.dead.letter}}";
    public static final String DIRECT_RETRY_HANDLER = "direct:retry-handler";
    public static final String DIRECT_POISON_DLQ = "direct:poison-dlq";
    public static final String DIRECT_DLQ_HANDLER = "direct:dlq-handler";

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
        errorHandler(deadLetterChannel(DIRECT_RETRY_HANDLER)
            .useOriginalMessage()
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .logRetryAttempted(true)
            .logExhausted(true));

        onException(JsonProcessingException.class, IllegalArgumentException.class)
            .handled(true)
            .maximumRedeliveries(0)
            .logExhausted(true)
            .log("Poison message, routing to DLQ without retry: ${exception.message}")
            .to(DIRECT_POISON_DLQ);

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

        from(ContentBasedRouterBean.DESTINATION_FRAUD_CHECK)
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

        from(DIRECT_POISON_DLQ)
            .routeId("poison-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Poison payment ${header.OriginalPaymentId}: ${exception.message}")
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(DEAD_LETTER_TOPIC_URI)
            .log("Published poison to dead letter queue");

        from(DIRECT_DLQ_HANDLER)
            .routeId("error-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Error processing payment ${header.OriginalPaymentId}: ${exception.message}")
            .setHeader("kafka.KEY", header("OriginalPaymentId"))
            .to(DEAD_LETTER_TOPIC_URI)
            .log("Published to dead letter queue");
    }
}
