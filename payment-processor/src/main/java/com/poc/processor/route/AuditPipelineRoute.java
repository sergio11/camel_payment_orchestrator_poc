package com.poc.processor.route;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@ApplicationScoped
public class AuditPipelineRoute extends RouteBuilder {

    @Override
    public void configure() {
        // Audit pipeline - WireTap destination
        from("direct:audit-pipeline")
            .routeId("audit-pipeline")
            .log("Auditing payment: ${body.paymentId}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.audit}}")
            .log("Published audit event for: ${body.paymentId}");
    }
}