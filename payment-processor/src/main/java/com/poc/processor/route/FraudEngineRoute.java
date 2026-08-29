package com.poc.processor.route;

import com.poc.shared.event.FraudResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@ApplicationScoped
public class FraudEngineRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:fraud-reject")
            .routeId("fraud-reject")
            .log("Payment REJECTED by fraud engine: ${body.paymentId}, score: ${header.CamelRiskScore}")
            .to("direct:publish-fraud-and-failed");

        from("direct:fraud-review-queue")
            .routeId("fraud-review-queue")
            .log("Payment queued for manual review: ${body.paymentId}")
            .to("direct:publish-fraud-detected");

        from("direct:publish-fraud-and-failed")
            .routeId("publish-fraud-and-failed")
            .onException(Exception.class)
                .handled(true)
                .log("Kafka publish failed for fraud-and-failed: ${exception.message}")
                .to("kafka:{{kafka.topic.dead.letter}}")
            .end()
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .to("kafka:{{kafka.topic.payments.failed}}")
            .log("Published fraud rejection and failed event for: ${body.paymentId}");

        from("direct:publish-fraud-detected")
            .routeId("publish-fraud-detected")
            .onException(Exception.class)
                .handled(true)
                .log("Kafka publish failed for fraud-detected: ${exception.message}")
                .to("kafka:{{kafka.topic.dead.letter}}")
            .end()
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .log("Published fraud detected event for: ${body.paymentId}");
    }
}
