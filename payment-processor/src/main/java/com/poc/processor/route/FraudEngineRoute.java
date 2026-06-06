package com.poc.processor.route;

import com.poc.shared.event.FraudResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@ApplicationScoped
public class FraudEngineRoute extends RouteBuilder {

    @Override
    public void configure() {
        // Fraud reject - publish to fraud events topic
        from("direct:fraud-reject")
            .routeId("fraud-reject")
            .log("Payment REJECTED by fraud engine: ${body.paymentId}, score: ${header.CamelRiskScore}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .log("Published fraud rejection event for: ${body.paymentId}");

        // Fraud review queue - for manual review
        from("direct:fraud-review-queue")
            .routeId("fraud-review-queue")
            .log("Payment queued for manual review: ${body.paymentId}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .log("Published fraud review event for: ${body.paymentId}");

        // Route for direct fraud processing (if needed)
        from("direct:fraud-engine")
            .routeId("fraud-engine-direct")
            .process("fraudEvaluationProcessor")
            .choice()
                .when(body().method("action").isEqualTo("REJECT"))
                    .to("direct:fraud-reject")
                .when(body().method("action").isEqualTo("REVIEW"))
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .to("direct:provider-selection")
            .end();
    }
}