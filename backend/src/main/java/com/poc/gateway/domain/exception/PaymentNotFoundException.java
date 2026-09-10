package com.poc.gateway.domain.exception;

public class PaymentNotFoundException extends DomainException {
    private final String paymentId;

    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public static PaymentNotFoundException forId(String paymentId) {
        return new PaymentNotFoundException(paymentId);
    }
}
