package com.poc.processor.domain.exception;

public class PaymentProcessingException extends RuntimeException {
    private final String paymentId;

    public PaymentProcessingException(String paymentId, String message) {
        super(message);
        this.paymentId = paymentId;
    }

    public PaymentProcessingException(String paymentId, String message, Throwable cause) {
        super(message, cause);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
