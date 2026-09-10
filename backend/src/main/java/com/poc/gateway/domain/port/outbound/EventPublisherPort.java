package com.poc.gateway.domain.port.outbound;

import com.poc.shared.dto.PaymentMetadataDTO;
import java.math.BigDecimal;

public interface EventPublisherPort {
    boolean publishPaymentReceived(
        String paymentId,
        BigDecimal amount,
        String currency,
        String customerId,
        String paymentMethod,
        String country,
        PaymentMetadataDTO metadata
    );

    boolean publishStatusChanged(String paymentId, String status);

    boolean publishDeadLetter(String paymentId, String payload, String reason);
}
