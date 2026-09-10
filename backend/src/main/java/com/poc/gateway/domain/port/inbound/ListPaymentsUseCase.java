package com.poc.gateway.domain.port.inbound;

import com.poc.gateway.domain.model.PaymentPageResult;
import com.poc.gateway.domain.model.PaymentStatus;

public interface ListPaymentsUseCase {
    PaymentPageResult execute(String customerId, PaymentStatus status, int limit, int offset);
}
