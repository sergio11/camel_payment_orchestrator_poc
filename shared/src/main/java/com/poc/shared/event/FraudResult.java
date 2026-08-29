package com.poc.shared.event;

import java.math.BigDecimal;
import java.util.List;

public sealed interface FraudResult 
    permits FraudResult.Approve, FraudResult.Review, FraudResult.Reject {
    
    String paymentId();
    BigDecimal amount();
    String customerId();
    int riskScore();
    String action();
    List<String> triggeredRules();
    
    static FraudResult approve(String paymentId, BigDecimal amount, String customerId, int riskScore, List<String> triggeredRules) {
        return new Approve(paymentId, amount, customerId, riskScore, triggeredRules);
    }
    
    static FraudResult review(String paymentId, BigDecimal amount, String customerId, int riskScore, String reason, List<String> triggeredRules) {
        return new Review(paymentId, amount, customerId, riskScore, reason, triggeredRules);
    }
    
    static FraudResult reject(String paymentId, BigDecimal amount, String customerId, int riskScore, String reason, List<String> triggeredRules) {
        return new Reject(paymentId, amount, customerId, riskScore, reason, triggeredRules);
    }
    
    record Approve(String paymentId, BigDecimal amount, String customerId, int riskScore, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return "APPROVE"; }
    }
    
    record Review(String paymentId, BigDecimal amount, String customerId, int riskScore, String reason, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return "REVIEW"; }
    }
    
    record Reject(String paymentId, BigDecimal amount, String customerId, int riskScore, String reason, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return "REJECT"; }
    }
}
