package com.poc.processor.domain;

import java.math.BigDecimal;
import java.util.List;

public record FraudEvaluation(
    String paymentId,
    BigDecimal amount,
    String customerId,
    int riskScore,
    String action,
    String reason,
    List<String> triggeredRules
) {
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REVIEW = "REVIEW";
    public static final String ACTION_REJECT = "REJECT";

    public boolean isApprove() {
        return ACTION_APPROVE.equals(action);
    }

    public boolean isReview() {
        return ACTION_REVIEW.equals(action);
    }

    public boolean isReject() {
        return ACTION_REJECT.equals(action);
    }
}
