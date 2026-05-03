package com.poc.gateway.repository;

import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class PaymentRepository {
    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();

    public Payment save(Payment payment) {
        payment.setId(UUID.randomUUID());
        payment.setCreatedAt(java.time.LocalDateTime.now());
        payment.setUpdatedAt(java.time.LocalDateTime.now());
        payment.setStatus(PaymentStatus.PENDING);
        store.put(payment.getId(), payment);
        return payment;
    }

    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset) {
        return store.values().stream()
            .filter(p -> customerId == null || p.getCustomerId().equals(customerId))
            .filter(p -> status == null || p.getStatus() == status)
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
    }

    public long count(String customerId, PaymentStatus status) {
        return store.values().stream()
            .filter(p -> customerId == null || p.getCustomerId().equals(customerId))
            .filter(p -> status == null || p.getStatus() == status)
            .count();
    }

    public Payment update(Payment payment) {
        payment.setUpdatedAt(java.time.LocalDateTime.now());
        store.put(payment.getId(), payment);
        return payment;
    }
}