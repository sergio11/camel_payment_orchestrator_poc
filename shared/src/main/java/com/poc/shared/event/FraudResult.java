package com.poc.shared.event;

import java.util.List;

public sealed interface FraudResult 
    permits FraudResult.Approve, FraudResult.Review, FraudResult.Reject {
    
    String paymentId();
    int riskScore();
    String action();
    List<String> triggeredRules();
    
    static FraudResult approve(String paymentId, int riskScore, List<String> triggeredRules) {
        return new Approve(paymentId, riskScore, triggeredRules);
    }
    
    static FraudResult review(String paymentId, int riskScore, String reason, List<String> triggeredRules) {
        return new Review(paymentId, riskScore, reason, triggeredRules);
    }
    
    static FraudResult reject(String paymentId, int riskScore, String reason, List<String> triggeredRules) {
        return new Reject(paymentId, riskScore, reason, triggeredRules);
    }
    
    record Approve(String paymentId, int riskScore, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return "APPROVE"; }
    }
    
    record Review(String paymentId, int riskScore, String reason, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return "REVIEW"; }
    }
    
    record Reject(String paymentId, int riskScore, String reason, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return "REJECT"; }
    }
}
