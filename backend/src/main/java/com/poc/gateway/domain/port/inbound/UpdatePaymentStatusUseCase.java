package com.poc.gateway.domain.port.inbound;

import com.poc.shared.dto.PaymentResponseDTO;

public interface UpdatePaymentStatusUseCase {
    PaymentResponseDTO execute(String paymentId, String newStatus);
}
