package com.poc.processor.port.outbound;

public interface AuditEventPublisherPort {
    void publishAudit(String paymentId, String eventType);
}
