package com.poc.gateway.domain.port.inbound;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentStatus;
import java.util.UUID;

public interface UpdatePaymentStatusUseCase {
    Payment execute(UUID paymentId, PaymentStatus newStatus);
}
