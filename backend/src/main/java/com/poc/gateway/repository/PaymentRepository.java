package com.poc.gateway.repository;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    Payment save(Payment payment, String idempotencyKey);
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findById(UUID id);
    List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset);
    long count(String customerId, PaymentStatus status);
    Payment update(UUID id, PaymentStatus newStatus);
    Optional<Payment> updateIfPending(UUID id, PaymentStatus newStatus);
    void deleteById(UUID id);

    static PaymentRepository inMemory() {
        return new PaymentRepositoryInMemory();
    }
}
