package com.poc.processor.domain.exception;

public class ProcessorDomainException extends RuntimeException {
    private final String paymentId;

    public ProcessorDomainException(String paymentId, String message) {
        super(message);
        this.paymentId = paymentId;
    }

    public ProcessorDomainException(String paymentId, String message, Throwable cause) {
        super(message, cause);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
