package com.poc.gateway.domain.port.inbound;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.CreatePaymentCommand;

public interface CreatePaymentUseCase {
    Payment execute(CreatePaymentCommand command, String idempotencyKey);
}
