package com.poc.gateway.repository;

import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentRepository {

    private static final Logger LOG = Logger.getLogger(PaymentRepository.class);
    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();

    public Payment save(Payment payment) {
        UUID id = payment.id() != null ? payment.id() : UUID.randomUUID();
        LOG.debugf("Saving payment: %s", id);
        Payment saved = new Payment(
            id,
            payment.amount(),
            payment.currency(),
            payment.customerId(),
            payment.paymentMethod(),
            payment.country(),
            payment.status() != null ? payment.status() : PaymentStatus.PENDING,
            payment.provider(),
            payment.failureReason(),
            payment.metadata(),
            payment.createdAt() != null ? payment.createdAt() : LocalDateTime.now(),
            LocalDateTime.now()
        );
        store.put(saved.id(), saved);
        return saved;
    }

    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset) {
        return store.values().stream()
            .filter(p -> customerId == null || p.customerId().equals(customerId))
            .filter(p -> status == null || p.status() == status)
            .skip(offset)
            .limit(limit)
            .toList();
    }

    public long count(String customerId, PaymentStatus status) {
        return store.values().stream()
            .filter(p -> customerId == null || p.customerId().equals(customerId))
            .filter(p -> status == null || p.status() == status)
            .count();
    }

    public Payment update(Payment payment, PaymentStatus newStatus) {
        Payment updated = payment.withStatus(newStatus);
        store.put(updated.id(), updated);
        return updated;
    }
}