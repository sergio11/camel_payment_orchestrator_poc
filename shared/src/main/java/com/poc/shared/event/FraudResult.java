package com.poc.shared.event;

public sealed interface FraudResult 
    permits FraudResult.Approve, FraudResult.Review, FraudResult.Reject {
    
    String paymentId();
    int riskScore();
    String action();
    
    static FraudResult approve(String paymentId, int riskScore) {
        return new Approve(paymentId, riskScore);
    }
    
    static FraudResult review(String paymentId, int riskScore, String reason) {
        return new Review(paymentId, riskScore, reason);
    }
    
    static FraudResult reject(String paymentId, int riskScore, String reason) {
        return new Reject(paymentId, riskScore, reason);
    }
    
    record Approve(String paymentId, int riskScore) implements FraudResult {
        @Override
        public String action() { return "APPROVE"; }
    }
    
    record Review(String paymentId, int riskScore, String reason) implements FraudResult {
        @Override
        public String action() { return "REVIEW"; }
    }
    
    record Reject(String paymentId, int riskScore, String reason) implements FraudResult {
        @Override
        public String action() { return "REJECT"; }
    }
}