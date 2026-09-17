package com.poc.shared.exception;

public class InvalidPaymentException extends RuntimeException {

    private final String paymentId;

    public InvalidPaymentException(String paymentId, String message) {
        super(message);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
