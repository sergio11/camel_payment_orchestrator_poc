package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.CreatePaymentCommand;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.inbound.CreatePaymentUseCase;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import com.poc.shared.util.IdempotencyKeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CreatePaymentService implements CreatePaymentUseCase {

    private static final Logger LOG = Logger.getLogger(CreatePaymentService.class);

    @Inject
    private PaymentRepositoryPort paymentRepo;

    @Inject
    private OutboxRepositoryPort outboxRepo;

    @Inject
    private EventPublisherPort eventPublisher;

    @Inject
    private PaymentEventSerializer serializer;

    @Inject
    private KafkaTopicConfig topicConfig;

    @Override
    public Payment execute(CreatePaymentCommand command, String idempotencyKey) {
        String key = IdempotencyKeyUtils.normalize(idempotencyKey);

        Optional<Payment> existing = paymentRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            LOG.infof("Idempotent replay for key %s -> payment %s", key, existing.get().id());
            return existing.get();
        }

        Payment domainPayment = Payment.create(
            command.amount(), command.currency(), command.customerId(),
            command.paymentMethod(), command.country(), command.metadata()
        );

        Payment saved = persistWithOutbox(domainPayment, key);
        attemptKafkaPublish(saved);
        return saved;
    }

    @Transactional
    Payment persistWithOutbox(Payment domainPayment, String idempotencyKey) {
        Payment saved = paymentRepo.save(domainPayment, idempotencyKey);
        String payload = serializer.serialize(saved);
        OutboxEvent event = OutboxEvent.create(saved.id(), topicConfig.received(), payload, idempotencyKey);
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
}
