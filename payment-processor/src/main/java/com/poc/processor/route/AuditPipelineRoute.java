package com.poc.processor.route;

import com.poc.processor.port.outbound.AuditEventPublisherPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class AuditPipelineRoute extends RouteBuilder {

    @Inject
    AuditEventPublisherPort auditPublisher;

    @Override
    public void configure() {
        from("direct:audit-pipeline")
            .routeId("audit-pipeline")
            .process(exchange -> {
                Object body = exchange.getIn().getBody();
                String paymentId = null;
                if (body != null) {
                    try {
                        var method = body.getClass().getMethod("paymentId");
                        paymentId = (String) method.invoke(body);
                    } catch (Exception e) {
                        paymentId = exchange.getIn().getHeader("OriginalPaymentId", String.class);
                    }
                }
                if (paymentId == null) {
                    paymentId = exchange.getIn().getHeader("OriginalPaymentId", String.class);
                }
                auditPublisher.publishAudit(paymentId, "PAYMENT_RECEIVED");
            })
            .log("Audit event published for: ${header.OriginalPaymentId}");
    }
}
