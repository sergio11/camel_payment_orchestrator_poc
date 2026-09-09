package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.gateway.entity.OutboxEventEntity;
import com.poc.gateway.entity.OutboxStatus;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.exception.PaymentNotFoundException;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.gateway.repository.OutboxEventRepository;
import com.poc.gateway.repository.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @Inject
    PaymentRepository repository;

    @Inject
    OutboxEventRepository outbox;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    @Inject
    PaymentMapper paymentMapper;

    @Inject
    ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Payment> idempotencyCache = new ConcurrentHashMap<>();

    private PaymentMapper getMapper() {
        return paymentMapper != null ? paymentMapper : PaymentMapper.INSTANCE;
    }

    public PaymentResponseDTO createPayment(PaymentRequestDTO request) {
        return createPayment(request, UUID.randomUUID().toString());
    }

    public PaymentResponseDTO createPayment(PaymentRequestDTO request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        Optional<Payment> existing = findExistingPayment(key);
        if (existing.isPresent()) {
            LOG.infof("Idempotent replay for key %s -> payment %s (no Kafka republish)", key, existing.get().id());
            return getMapper().toResponseDTO(existing.get());
        }
        Payment payment = getMapper().toDomain(request);
        Payment saved = persistWithOutbox(payment, key);
        idempotencyCache.putIfAbsent(key, saved);
        boolean published = kafkaEventPublisher.publishPaymentReceived(
            saved.id().toString(),
            saved.amount(),
            saved.currency(),
            saved.customerId(),
            saved.paymentMethod(),
            saved.country(),
            saved.metadata()
        );
        if (published) {
            markOutboxSent(saved.id());
        } else {
            LOG.errorf("Kafka publish failed for payment %s, left as PENDING for OutboxRelay retry", saved.id());
            throw new RuntimeException("Failed to publish payment event to Kafka. Payment " + saved.id() + " left as PENDING.");
        }
        return getMapper().toResponseDTO(saved);
    }

    public Optional<PaymentResponseDTO> getByIdempotencyKey(String idempotencyKey) {
        return findExistingPayment(normalizeKey(idempotencyKey)).map(getMapper()::toResponseDTO);
    }

    Optional<Payment> findExistingPayment(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Payment cached = idempotencyCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            if (outbox != null) {
                Optional<OutboxEventEntity> evt = outbox.findByIdempotencyKey(key);
                if (evt.isPresent()) {
                    UUID agg = evt.get().aggregateId;
                    if (agg != null) {
                        Optional<Payment> p = repository.findById(agg);
                        p.ifPresent(payment -> idempotencyCache.putIfAbsent(key, payment));
                        if (p.isPresent()) {
                            return p;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Idempotency outbox lookup failed for key %s (best-effort)", key);
        }
        try {
            if (repository != null) {
                Optional<Payment> p = repository.findByIdempotencyKey(key);
                p.ifPresent(payment -> idempotencyCache.putIfAbsent(key, payment));
                return p;
            }
        } catch (Exception e) {
            LOG.warnf(e, "Idempotency payment lookup failed for key %s (best-effort)", key);
        }
        return Optional.empty();
    }

    String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return key.trim();
    }

    @Transactional
    Payment persistWithOutbox(Payment payment) {
        return persistWithOutbox(payment, UUID.randomUUID().toString());
    }

    @Transactional
    Payment persistWithOutbox(Payment payment, String idempotencyKey) {
        Payment saved = repository.save(payment, idempotencyKey);
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = UUID.randomUUID();
        event.aggregateId = saved.id();
        event.type = "payments.events.received";
        event.payload = buildPayload(saved);
        event.status = OutboxStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        event.idempotencyKey = idempotencyKey;
        if (outbox != null && outbox.em != null) {
            outbox.persist(event);
        } else {
            LOG.debugf("Outbox skipped (no EntityManager, unit-test mode) for payment %s", saved.id());
        }
        return saved;
    }

    void markOutboxSent(UUID aggregateId) {
        try {
            if (outbox == null || outbox.em == null) {
                return;
            }
            List<OutboxEventEntity> pending = outbox.findPending(100);
            for (OutboxEventEntity e : pending) {
                if (aggregateId.equals(e.aggregateId)) {
                    outbox.markSent(e.id);
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Could not mark outbox SENT for %s (best-effort)", aggregateId);
        }
    }

    String buildPayload(Payment saved) {
        try {
            ObjectMapper om = objectMapper != null ? objectMapper : new ObjectMapper();
            return om.writeValueAsString(Map.of(
                "paymentId", saved.id().toString(),
                "amount", saved.amount() != null ? saved.amount().toString() : "0",
                "currency", String.valueOf(saved.currency()),
                "customerId", String.valueOf(saved.customerId()),
                "paymentMethod", String.valueOf(saved.paymentMethod()),
                "country", String.valueOf(saved.country()),
                "metadata", saved.metadata() != null ? saved.metadata() : Map.of()
            ));
        } catch (Exception e) {
            return "{\"paymentId\":\"" + saved.id() + "\"}";
        }
    }

    public PaymentResponseDTO getPayment(String id) {
        if (id == null) {
            throw new PaymentNotFoundException("null");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new PaymentNotFoundException(id);
        }
        Payment payment = repository.findById(uuid)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        return getMapper().toResponseDTO(payment);
    }

    public List<PaymentResponseDTO> listPayments(String customerId, String status, int limit, int offset) {
        PaymentStatus paymentStatus = null;
        if (status != null) {
            try {
                paymentStatus = PaymentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid payment status: " + status);
            }
        }
        return repository.findAll(customerId, paymentStatus, limit, offset)
            .stream()
            .map(getMapper()::toResponseDTO)
            .toList();
    }

    public long countPayments(String customerId, String status) {
        PaymentStatus paymentStatus = null;
        if (status != null) {
            try {
                paymentStatus = PaymentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid payment status: " + status);
            }
        }
        return repository.count(customerId, paymentStatus);
    }
}
