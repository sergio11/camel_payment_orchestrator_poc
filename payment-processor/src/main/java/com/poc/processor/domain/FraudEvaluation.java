package com.poc.processor.domain;

import java.math.BigDecimal;
import java.util.List;

public record FraudEvaluation(
    String paymentId,
    BigDecimal amount,
    String customerId,
    int riskScore,
    FraudAction action,
    String reason,
    List<String> triggeredRules
) {
    public boolean isApprove() {
        return FraudAction.APPROVE.equals(action);
    }

    public boolean isReview() {
        return FraudAction.REVIEW.equals(action);
    }

    public boolean isReject() {
        return FraudAction.REJECT.equals(action);
    }
}
