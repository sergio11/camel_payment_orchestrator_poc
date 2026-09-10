package com.poc.gateway.domain.port.outbound;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentReadRepository {
    Optional<Payment> findById(UUID id);
    Optional<Payment> findByIdempotencyKey(String key);
    List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset);
    long count(String customerId, PaymentStatus status);
}
