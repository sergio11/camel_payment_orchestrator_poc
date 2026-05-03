package com.poc.gateway.exception;

public class PaymentNotFoundException extends RuntimeException {
    private final String paymentId;
    
    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
        this.paymentId = paymentId;
    }
    
    public String getPaymentId() {
        return paymentId;
    }
}