package com.poc.processor.domain;

import com.poc.shared.event.PaymentMessage;

public record PaymentContext(
    PaymentMessage message,
    String originalPaymentId,
    String originalEventId,
    String correlationId
) {
    public static PaymentContext from(PaymentMessage message) {
        return new PaymentContext(
            message,
            message.paymentId(),
            message.eventId(),
            message.paymentId()
        );
    }
}
