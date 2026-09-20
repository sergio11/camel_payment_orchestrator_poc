package com.poc.gateway.infrastructure.messaging.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class OutboxRelayScheduler {

    private static final Logger LOG = Logger.getLogger(OutboxRelayScheduler.class);

    @Inject
    private OutboxRepositoryPort outboxRepo;

    @Inject
    private EventPublisherPort eventPublisher;

    @Inject
    private ObjectMapper objectMapper;

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
        PaymentReceivedEvent paymentEvent = objectMapper.readValue(event.payload(), PaymentReceivedEvent.class);
        boolean published = eventPublisher.publishPaymentReceived(paymentEvent);
        if (!published) {
            throw new RuntimeException("Event publisher returned false for event " + event.id());
        }
    }
}
