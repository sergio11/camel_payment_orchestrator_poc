package com.poc.gateway.domain.port.inbound;

import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;

public interface CreatePaymentUseCase {
    PaymentResponseDTO execute(PaymentRequestDTO request, String idempotencyKey);
}
