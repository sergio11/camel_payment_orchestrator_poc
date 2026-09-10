package com.poc.gateway.domain.port.inbound;

import com.poc.shared.dto.PaymentResponseDTO;
import java.util.Optional;

public interface GetPaymentUseCase {
    PaymentResponseDTO execute(String paymentId);
    Optional<PaymentResponseDTO> executeByIdempotencyKey(String idempotencyKey);
}
