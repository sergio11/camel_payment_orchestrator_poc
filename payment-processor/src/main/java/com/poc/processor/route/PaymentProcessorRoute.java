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
    private ObjectMapper objectMapper;

    @Inject
    private FraudRoutingService fraudRoutingService;

    @Inject
    private EnrichPaymentUseCase enrichPaymentUseCase;

    @Inject
    private EvaluateFraudUseCase evaluateFraudUseCase;

    public static void restorePaymentIdFromKafkaKey(org.apache.camel.Exchange exchange) {
        CamelRouteConstants.setHeaderIfAbsent(exchange, CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID,
            exchange.getMessage().getHeader(CamelRouteConstants.HEADER_KAFKA_KEY));
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

        onException(JsonProcessingException.class, com.poc.processor.domain.exception.PaymentProcessingException.class, com.poc.shared.exception.InvalidPaymentException.class, com.poc.processor.domain.exception.FraudEvaluationException.class)
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
                msg.validate();
                CamelRouteConstants.setHeaderIfAbsent(exchange, CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, msg.paymentId());
                CamelRouteConstants.setHeaderIfAbsent(exchange, CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, msg.eventId());
            })
            .log("Received payment: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                PaymentMessage enriched = enrichPaymentUseCase.enrich(msg);
                exchange.getIn().setBody(enriched);
            })
            .wireTap("direct:audit-pipeline")
            .process(exchange -> {
                PaymentMessage msg = exchange.getIn().getBody(PaymentMessage.class);
                exchange.getIn().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, msg);

                FraudEvaluation evaluation = evaluateFraudUseCase.evaluate(msg);
                exchange.setProperty("FraudEvaluation", evaluation);

                FraudRoutingDecision decision = fraudRoutingService.route(msg, evaluation);
                exchange.getIn().setHeader(CamelRouteConstants.HEADER_FRAUD_ACTION, decision.action().name());
                exchange.getIn().setHeader(CamelRouteConstants.HEADER_RISK_SCORE, decision.evaluation().riskScore());
                exchange.getIn().setHeader(CamelRouteConstants.HEADER_FRAUD_ROUTE_TARGET, decision.routeTarget());
            })
            .log("Fraud route target: ${header." + CamelRouteConstants.HEADER_FRAUD_ROUTE_TARGET + "} for ${body.paymentId}")
            .choice()
                .when(header(CamelRouteConstants.HEADER_FRAUD_ROUTE_TARGET).isEqualTo(CamelRouteConstants.DIRECT_FRAUD_REVIEW))
                    .log("High amount or WALLET payment, routing to fraud review: ${body.paymentId}")
                    .to(CamelRouteConstants.DIRECT_FRAUD_REVIEW)
                .otherwise()
                    .log("Standard payment, routing to fraud check: ${body.paymentId}")
                    .to(CamelRouteConstants.DIRECT_FRAUD_CHECK)
            .end()
            .log("Payment processed: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "} -> ${header." + CamelRouteConstants.HEADER_FRAUD_ACTION + "}");

        from(CamelRouteConstants.DIRECT_FRAUD_REVIEW)
            .routeId("fraud-review-high-value")
            .log("Fraud evaluation already done for high-value payment: ${body.paymentId}")
            .choice()
                .when(header(CamelRouteConstants.HEADER_FRAUD_ACTION).isEqualTo(com.poc.processor.domain.FraudAction.REJECT.name()))
                    .log("Fraud REJECT for high-value payment: ${body.paymentId}")
                    .to("direct:fraud-reject")
                .when(header(CamelRouteConstants.HEADER_FRAUD_ACTION).isEqualTo(com.poc.processor.domain.FraudAction.REVIEW.name()))
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
                .when(header(CamelRouteConstants.HEADER_FRAUD_ACTION).isEqualTo(com.poc.processor.domain.FraudAction.REJECT.name()))
                    .log("Fraud REJECT: ${body.paymentId}")
                    .to("direct:fraud-reject")
                .when(header(CamelRouteConstants.HEADER_FRAUD_ACTION).isEqualTo(com.poc.processor.domain.FraudAction.REVIEW.name()))
                    .log("Fraud REVIEW: ${body.paymentId}")
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .log("Fraud APPROVE: ${body.paymentId}")
                    .to("direct:provider-selection")
            .end();

        from(CamelRouteConstants.DIRECT_RETRY_HANDLER)
            .routeId("retry-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Exhausted retries for payment ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}: ${exception.message}")
            .choice()
                .when(body().isInstanceOf(PaymentMessage.class))
                    .marshal(paymentJson)
            .end()
            .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID))
            .to(CamelRouteConstants.RETRY_TOPIC_URI)
            .log("Published to retry topic");

        from(CamelRouteConstants.DIRECT_POISON_DLQ)
            .routeId("poison-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Poison payment ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}: ${exception.message}")
            .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID))
            .to(CamelRouteConstants.DEAD_LETTER_TOPIC_URI)
            .log("Published poison to dead letter queue");

        from(CamelRouteConstants.DIRECT_DLQ_HANDLER)
            .routeId("error-dlq-handler")
            .process(PaymentProcessorRoute::restorePaymentIdFromKafkaKey)
            .log("Error processing payment ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}: ${exception.message}")
            .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID))
            .to(CamelRouteConstants.DEAD_LETTER_TOPIC_URI)
            .log("Published to dead letter queue");

        from("kafka:{{kafka.topic.payments.retry}}?groupId=payment-processor-retry-group&autoCommitEnable=false&autoOffsetReset=earliest")
            .routeId("retry-consumer")
            .autoStartup("{{camel.route.retry-consumer.auto-startup:true}}")
            .log("Retrying payment from retry topic: ${header." + CamelRouteConstants.HEADER_KAFKA_KEY + "}")
            .process(exchange -> {
                Object raw = exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT);
                int retryCount = 0;
                if (raw instanceof Integer) {
                    retryCount = (Integer) raw;
                } else if (raw instanceof Number) {
                    retryCount = ((Number) raw).intValue();
                } else if (raw instanceof byte[]) {
                    try { retryCount = Integer.parseInt(new String((byte[]) raw).trim()); } catch (Exception ignored) {}
                } else if (raw != null) {
                    try { retryCount = Integer.parseInt(raw.toString().trim()); } catch (Exception ignored) {}
                }
                exchange.getIn().setHeader(CamelRouteConstants.HEADER_RETRY_COUNT, retryCount + 1);
            })
            .choice()
                .when(header(CamelRouteConstants.HEADER_RETRY_COUNT).isLessThan(3))
                    .log("Retry attempt ${header." + CamelRouteConstants.HEADER_RETRY_COUNT + "} for ${header." + CamelRouteConstants.HEADER_KAFKA_KEY + "}")
                    .to("kafka:{{kafka.topic.payments.received}}")
                .otherwise()
                    .log("Max retries exceeded for ${header." + CamelRouteConstants.HEADER_KAFKA_KEY + "}, sending to DLQ")
                    .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_KAFKA_KEY))
                    .to(CamelRouteConstants.DEAD_LETTER_TOPIC_URI)
            .end();
    }
}
