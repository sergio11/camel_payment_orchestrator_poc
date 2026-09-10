package com.poc.gateway.domain.port.outbound;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentStatus;
import java.util.Optional;
import java.util.UUID;

public interface PaymentWriteRepository {
    Payment save(Payment payment);
    Payment save(Payment payment, String idempotencyKey);
    Payment update(UUID id, PaymentStatus newStatus);
    Optional<Payment> updateIfPending(UUID id, PaymentStatus newStatus);
    void deleteById(UUID id);
}
