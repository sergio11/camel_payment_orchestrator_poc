package com.poc.gateway.infrastructure.messaging.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class OutboxRelayScheduler {

    private static final Logger LOG = Logger.getLogger(OutboxRelayScheduler.class);

    @Inject
    OutboxRepositoryPort outboxRepo;

    @Inject
    EventPublisherPort eventPublisher;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "outbox.relay.batch-size", defaultValue = "50")
    int batchSize;

    @ConfigProperty(name = "outbox.relay.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(every = "10s")
    void processPendingEvents() {
        if (!enabled) {
            return;
        }

        List<OutboxEvent> pendingEvents = outboxRepo.findPending(batchSize);
        if (pendingEvents.isEmpty()) {
            return;
        }

        LOG.debugf("Processing %d pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                outboxRepo.markSent(event.id());
                LOG.debugf("Successfully published outbox event %s", event.id());
            } catch (Exception e) {
                LOG.errorf(e, "Failed to publish outbox event %s, will retry on next cycle", event.id());
            }
        }
    }

    private void publishEvent(OutboxEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(event.payload());
        String paymentId = payload.has("paymentId") ? payload.get("paymentId").asText() : event.aggregateId().toString();
        BigDecimal amount = payload.has("amount") ? new BigDecimal(payload.get("amount").asText()) : BigDecimal.ZERO;
        String currency = payload.has("currency") ? payload.get("currency").asText() : "";
        String customerId = payload.has("customerId") ? payload.get("customerId").asText() : "";
        String paymentMethod = payload.has("paymentMethod") ? payload.get("paymentMethod").asText() : "";
        String country = payload.has("country") ? payload.get("country").asText() : "";

        PaymentMetadata metadata = null;
        if (payload.has("metadata")) {
            JsonNode metadataNode = payload.get("metadata");
            if (!metadataNode.isNull() && !metadataNode.isMissingNode()) {
                metadata = objectMapper.convertValue(metadataNode, PaymentMetadata.class);
            }
        }

        PaymentReceivedEvent paymentEvent = new PaymentReceivedEvent(
            paymentId, amount, currency, customerId, paymentMethod, country, metadata
        );
        eventPublisher.publishPaymentReceived(paymentEvent);
    }
}
