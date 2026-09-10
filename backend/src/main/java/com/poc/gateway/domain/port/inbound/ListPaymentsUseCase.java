package com.poc.gateway.domain.port.inbound;

import com.poc.shared.dto.PaymentPageResponseDTO;

public interface ListPaymentsUseCase {
    PaymentPageResponseDTO execute(String customerId, String status, int limit, int offset);
}
