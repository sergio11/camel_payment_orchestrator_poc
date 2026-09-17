package com.poc.gateway.infrastructure.messaging.consumer;

import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentWriteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentStatusHandler {

    private static final Logger LOG = Logger.getLogger(PaymentStatusHandler.class);

    @Inject
    private PaymentWriteRepository paymentRepo;

    @Inject
    private EventPublisherPort eventPublisher;

    @Inject
    private ObjectMapper objectMapper;

    public void processStatusChange(ConsumerRecord<String, String> record, PaymentStatus newStatus) {
        String paymentId = extractPaymentId(record);
        if (paymentId == null) {
            LOG.warn("Could not extract paymentId from record, sending to DLQ");
            sendToDeadLetter(record, "No paymentId in record");
            return;
        }

        try {
            paymentRepo.updateIfPending(UUID.fromString(paymentId), newStatus);
            eventPublisher.publishStatusChanged(paymentId, newStatus.name());
            LOG.infof("Processed status change for payment %s to %s", paymentId, newStatus);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process status change for payment %s", paymentId);
            sendToDeadLetter(record, e.getMessage());
        }
    }

    public void sendToDeadLetter(ConsumerRecord<String, String> record, String reason) {
        try {
            String paymentId = extractPaymentId(record);
            eventPublisher.publishDeadLetter(paymentId, record.value(), reason);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send to dead letter for record");
        }
    }

    private String extractPaymentId(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            return json.has("paymentId") ? json.get("paymentId").asText() : record.key();
        } catch (Exception e) {
            return record.key();
        }
    }
}
