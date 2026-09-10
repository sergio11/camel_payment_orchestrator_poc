package com.poc.gateway.domain.port.inbound;

import com.poc.gateway.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface GetPaymentUseCase {
    Payment execute(UUID paymentId);
    Optional<Payment> executeByIdempotencyKey(String idempotencyKey);
}
