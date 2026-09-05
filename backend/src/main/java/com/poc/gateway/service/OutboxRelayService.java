package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.entity.OutboxEventEntity;
import com.poc.gateway.entity.Payment;
import com.poc.gateway.repository.OutboxEventRepository;
import com.poc.gateway.repository.PaymentRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OutboxRelayService {

    private static final Logger LOG = Logger.getLogger(OutboxRelayService.class);

    @Inject
    OutboxEventRepository outbox;

    @Inject
    PaymentRepository payments;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    @Inject
    ObjectMapper objectMapper;

    @Scheduled(every = "10s", identity = "outbox-relay")
    void relayPending() {
        List<OutboxEventEntity> pending;
        try {
            pending = outbox.findPending(50);
        } catch (Exception e) {
            LOG.debugf(e, "Outbox relay skipped (DB unavailable)");
            return;
        }
        for (OutboxEventEntity event : pending) {
            try {
                boolean ok = republish(event);
                if (ok) {
                    outbox.markSent(event.id);
                    LOG.infof("Outbox relay sent event %s for payment %s", event.id, event.aggregateId);
                }
            } catch (Exception e) {
                LOG.warnf(e, "Outbox relay failed for event %s", event.id);
            }
        }
    }

    @SuppressWarnings("unchecked")
    boolean republish(OutboxEventEntity event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.payload, Map.class);
            String paymentId = String.valueOf(payload.getOrDefault("paymentId", event.aggregateId.toString()));
            Payment p = payments.findById(event.aggregateId).orElse(null);
            if (p == null) {
                LOG.warnf("Outbox relay: payment %s not found, marking sent to avoid poison", event.aggregateId);
                return true;
            }
            return kafkaEventPublisher.publishPaymentReceived(
                paymentId, p.amount(), p.currency(), p.customerId(),
                p.paymentMethod(), p.country(), p.metadata()
            );
        } catch (Exception e) {
            LOG.warnf(e, "Outbox relay republish failed for %s", event.id);
            return false;
        }
    }
}
