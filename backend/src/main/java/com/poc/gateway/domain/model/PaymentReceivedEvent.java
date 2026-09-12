package com.poc.gateway.domain.model;

import com.poc.gateway.domain.PaymentMetadata;
import java.math.BigDecimal;

public record PaymentReceivedEvent(
    String paymentId,
    BigDecimal amount,
    String currency,
    String customerId,
    String paymentMethod,
    String country,
    PaymentMetadata metadata
) {
    public static PaymentReceivedEvent from(com.poc.gateway.domain.Payment payment) {
        return new PaymentReceivedEvent(
            payment.id().toString(),
            payment.amount(),
            payment.currency(),
            payment.customerId(),
            payment.paymentMethod(),
            payment.country(),
            payment.metadata()
        );
    }
}
