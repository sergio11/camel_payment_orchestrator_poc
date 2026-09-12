package com.poc.gateway.application.service;

import com.poc.gateway.application.mapper.PaymentMetadataApplicationMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.inbound.CreatePaymentUseCase;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.shared.dto.PaymentRequestDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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
    PaymentMetadataApplicationMapper metadataMapper;

    @Inject
    Instance<CreatePaymentService> self;

    @Override
    public Payment execute(PaymentRequestDTO request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        Optional<Payment> existing = paymentRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            LOG.infof("Idempotent replay for key %s -> payment %s", key, existing.get().id());
            return existing.get();
        }

        PaymentMetadata metadata = metadataMapper.toDomain(request.metadata());
        Payment domainPayment = Payment.create(
            request.amount(), request.currency(), request.customerId(),
            request.paymentMethod(), request.country(), metadata
        );

        Payment saved = self.get().persistWithOutbox(domainPayment, key);
        attemptKafkaPublish(saved);
        return saved;
    }

    @Transactional
    Payment persistWithOutbox(Payment domainPayment, String idempotencyKey) {
        Payment saved = paymentRepo.save(domainPayment, idempotencyKey);
        String payload = serializer.serialize(saved);
        OutboxEvent event = OutboxEvent.create(saved.id(), "payments.events.received", payload, idempotencyKey);
        outboxRepo.persist(event);
        return saved;
    }

    private void attemptKafkaPublish(Payment payment) {
        try {
            eventPublisher.publishPaymentReceived(PaymentReceivedEvent.from(payment));
        } catch (Exception e) {
            LOG.errorf(e, "Kafka publish attempt failed for payment %s, outbox relay will handle delivery", payment.id());
        }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return key.trim();
    }
}
