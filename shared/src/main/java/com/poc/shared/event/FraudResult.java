package com.poc.shared.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FraudResult.Approve.class, name = "APPROVE"),
    @JsonSubTypes.Type(value = FraudResult.Review.class, name = "REVIEW"),
    @JsonSubTypes.Type(value = FraudResult.Reject.class, name = "REJECT")
})
public sealed interface FraudResult 
    permits FraudResult.Approve, FraudResult.Review, FraudResult.Reject {
    
    String ACTION_APPROVE = "APPROVE";
    String ACTION_REVIEW = "REVIEW";
    String ACTION_REJECT = "REJECT";

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
        public String action() { return ACTION_APPROVE; }
    }
    
    record Review(String paymentId, BigDecimal amount, String customerId, int riskScore, String reason, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return ACTION_REVIEW; }
    }
    
    record Reject(String paymentId, BigDecimal amount, String customerId, int riskScore, String reason, List<String> triggeredRules) implements FraudResult {
        @Override
        public String action() { return ACTION_REJECT; }
    }
}
