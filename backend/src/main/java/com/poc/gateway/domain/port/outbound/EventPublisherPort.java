package com.poc.gateway.domain.port.outbound;

import com.poc.gateway.domain.PaymentMetadata;
import java.math.BigDecimal;

public interface EventPublisherPort {
    boolean publishPaymentReceived(
        String paymentId,
        BigDecimal amount,
        String currency,
        String customerId,
        String paymentMethod,
        String country,
        PaymentMetadata metadata
    );

    boolean publishStatusChanged(String paymentId, String status);

    boolean publishDeadLetter(String paymentId, String payload, String reason);
}
