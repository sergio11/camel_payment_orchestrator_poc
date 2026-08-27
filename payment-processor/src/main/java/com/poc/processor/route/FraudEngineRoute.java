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
            .onException(Exception.class)
                .handled(true)
                .log("Kafka publish failed for fraud-reject: ${exception.message}")
                .to("kafka:{{kafka.topic.dead.letter}}")
            .end()
            .log("Payment REJECTED by fraud engine: ${body.paymentId}, score: ${header.CamelRiskScore}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .to("kafka:{{kafka.topic.payments.failed}}")
            .log("Published fraud rejection and failed event for: ${body.paymentId}");

        from("direct:fraud-review-queue")
            .routeId("fraud-review-queue")
            .onException(Exception.class)
                .handled(true)
                .log("Kafka publish failed for fraud-review-queue: ${exception.message}")
                .to("kafka:{{kafka.topic.dead.letter}}")
            .end()
            .log("Payment queued for manual review: ${body.paymentId}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .log("Published fraud review event for: ${body.paymentId}");
    }
}
