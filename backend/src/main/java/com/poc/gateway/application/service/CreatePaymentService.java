package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.port.inbound.CreatePaymentUseCase;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.PaymentMetadataDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CreatePaymentService implements CreatePaymentUseCase {

    private static final Logger LOG = Logger.getLogger(CreatePaymentService.class);

    @Inject
    PaymentRepositoryPort paymentRepo;

    @Inject
    OutboxRepositoryPort outboxRepo;

    @Inject
    EventPublisherPort eventPublisher;

    @Inject
    PaymentEventSerializer serializer;

    @Inject
    PaymentMapper mapper;

    @Override
    @Transactional
    public PaymentResponseDTO execute(PaymentRequestDTO request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        Optional<Payment> existing = paymentRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            LOG.infof("Idempotent replay for key %s -> payment %s", key, existing.get().id());
            return mapper.toResponseDTO(existing.get());
        }

        Payment payment = mapper.toDomain(request);
        Payment saved = paymentRepo.save(payment, key);

        String payload = serializer.serialize(saved);
        OutboxEvent event = OutboxEvent.create(saved.id(), "payments.events.received", payload, key);
        outboxRepo.persist(event);

        boolean published = eventPublisher.publishPaymentReceived(
            saved.id().toString(),
            saved.amount(),
            saved.currency(),
            saved.customerId(),
            saved.paymentMethod(),
            saved.country(),
            mapper.toMetadataDTO(saved.metadata())
        );

        if (published) {
            outboxRepo.markSent(event.id());
        } else {
            LOG.errorf("Kafka publish failed for payment %s, left as PENDING for OutboxRelay retry", saved.id());
        }

        return mapper.toResponseDTO(saved);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return key.trim();
    }
}
