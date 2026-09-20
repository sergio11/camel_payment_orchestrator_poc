package com.poc.processor.domain.exception;

public class PaymentProcessingException extends ProcessorDomainException {
    public PaymentProcessingException(String paymentId, String message) {
        super(paymentId, message);
    }

    public PaymentProcessingException(String paymentId, String message, Throwable cause) {
        super(paymentId, message, cause);
    }
}
