package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.processor.ContentBasedRouterBean;
import com.poc.processor.processor.FraudEvaluationProcessor;
import com.poc.processor.processor.PaymentEnrichProcessor;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@ApplicationScoped
public class PaymentProcessorRoute extends RouteBuilder {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void configure() {
        errorHandler(deadLetterChannel("direct:dlq-handler")
            .logRetryAttempted(true)
            .logExhausted(true));

        var paymentJson = new org.apache.camel.component.jackson.JacksonDataFormat(objectMapper, PaymentMessage.class);

        from("kafka:{{kafka.topic.payments.received}}?groupId=payment-processor-group&autoCommitEnable=false&autoOffsetReset=earliest&allowManualCommit=true")
            .routeId("payment-processor")
            .autoStartup("{{camel.route.payment-processor.auto-startup:true}}")
            .unmarshal(paymentJson)
            .log("Received payment: ${body.paymentId}")
            .wireTap("direct:audit-pipeline")
            .process("paymentEnrichProcessor")
            .choice()
                .when(method(ContentBasedRouterBean.class, "routeToFraudCheck(${body.amount}, ${body.paymentMethod}, ${body.country})").isEqualTo("direct:fraud-review"))
                    .log("High amount or WALLET payment, routing to fraud review: ${body.paymentId}")
                    .to("direct:fraud-review")
                .otherwise()
                    .log("Standard payment, routing to fraud check: ${body.paymentId}")
                    .to("direct:fraud-check")
            .end()
            .log("Payment processed: ${header.OriginalPaymentMessage.paymentId} -> ${header.CamelFraudAction}")
            .process(exchange -> {
                exchange.getMessage().setHeader("CamelKafkaManualCommit", true);
            });

        from("direct:fraud-review")
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

        from("direct:fraud-check")
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

        from("direct:dlq-handler")
            .routeId("error-dlq-handler")
            .log("Error processing payment: ${exception.message}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.dead.letter}}")
            .log("Published to dead letter queue");
    }
}
