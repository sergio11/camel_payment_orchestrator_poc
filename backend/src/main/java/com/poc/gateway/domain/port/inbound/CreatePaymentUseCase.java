package com.poc.gateway.domain.port.inbound;

import com.poc.gateway.domain.Payment;

public interface CreatePaymentUseCase {
    Payment execute(Payment payment, String idempotencyKey);
}
