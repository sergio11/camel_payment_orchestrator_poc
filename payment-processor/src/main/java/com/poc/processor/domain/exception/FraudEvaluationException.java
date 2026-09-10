package com.poc.processor.domain.exception;

public class FraudEvaluationException extends RuntimeException {
    private final String paymentId;

    public FraudEvaluationException(String paymentId, String message) {
        super(message);
        this.paymentId = paymentId;
    }

    public FraudEvaluationException(String paymentId, String message, Throwable cause) {
        super(message, cause);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
