package com.poc.gateway.repository;

import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PaymentRepository {
    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();

    public Payment save(Payment payment) {
        Payment saved = Payment.create(
            payment.amount(),
            payment.currency(),
            payment.customerId(),
            payment.paymentMethod(),
            payment.country(),
            payment.metadata()
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

    public Payment update(Payment payment) {
        Payment updated = payment.withStatus(payment.status());
        store.put(updated.id(), updated);
        return updated;
    }
}