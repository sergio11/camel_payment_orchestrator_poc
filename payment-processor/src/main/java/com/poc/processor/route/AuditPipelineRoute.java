package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;

@ApplicationScoped
public class AuditPipelineRoute extends RouteBuilder {

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public void configure() {
        var auditJson = new JacksonDataFormat(objectMapper, Object.class);

        from("direct:audit-pipeline")
            .routeId("audit-pipeline")
            .log("Auditing payment: ${body.paymentId}")
            .setHeader("AuditPaymentId", simple("${body.paymentId}"))
            .setHeader("kafka.KEY", simple("${body.paymentId}"))
            .marshal(auditJson)
            .to("kafka:{{kafka.topic.audit}}")
            .log("Published audit event for: ${header.AuditPaymentId}");
    }
}
