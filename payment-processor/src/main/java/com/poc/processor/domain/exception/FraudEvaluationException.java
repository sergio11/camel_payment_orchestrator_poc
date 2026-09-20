package com.poc.processor.domain.exception;

public class FraudEvaluationException extends ProcessorDomainException {
    public FraudEvaluationException(String paymentId, String message) {
        super(paymentId, message);
    }

    public FraudEvaluationException(String paymentId, String message, Throwable cause) {
        super(paymentId, message, cause);
    }
}
