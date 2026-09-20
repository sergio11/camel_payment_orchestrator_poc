package com.poc.gateway.domain.exception;

import com.poc.gateway.domain.model.PaymentStatus;

public class InvalidPaymentTransitionException extends DomainException {
    private final PaymentStatus from;
    private final PaymentStatus to;

    public InvalidPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid payment transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public PaymentStatus getFrom() {
        return from;
    }

    public PaymentStatus getTo() {
        return to;
    }
}
