package com.poc.processor.route;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@ApplicationScoped
public class PaymentProcessorRoute extends RouteBuilder {

    @Override
    public void configure() {
        // Main payment processing route - consumes from Kafka
        from("kafka:{{kafka.topic.payments.received}}")
            .routeId("payment-processor")
            .autoStartup(true)
            .log("Received payment: ${body.paymentId}")
            .wireTap("direct:audit-pipeline")
            .process("paymentEnrichProcessor")
            .choice()
                .when(simple("${body.amount} > 10000"))
                    .log("High amount payment, routing to fraud review: ${body.paymentId}")
                    .to("direct:fraud-review")
                .when(simple("${body.paymentMethod} == 'WALLET' && ${body.amount} > 5000"))
                    .log("High value WALLET payment, routing to fraud review: ${body.paymentId}")
                    .to("direct:fraud-review")
                .otherwise()
                    .log("Standard payment, routing to fraud check: ${body.paymentId}")
                    .to("direct:fraud-check")
            .end();

        // Fraud review for high-value payments
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

        // Standard fraud check
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
    }
}