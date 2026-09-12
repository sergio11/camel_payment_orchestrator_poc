package com.poc.gateway.domain.port.inbound;

import com.poc.gateway.domain.Payment;
import com.poc.shared.dto.PaymentRequestDTO;

public interface CreatePaymentUseCase {
    Payment execute(PaymentRequestDTO request, String idempotencyKey);
}
