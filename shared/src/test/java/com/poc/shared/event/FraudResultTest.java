package com.poc.shared.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FraudResultTest {

    private static final String PAYMENT_ID = "pay-123";
    private static final BigDecimal AMOUNT = new BigDecimal("250.00");
    private static final String CUSTOMER_ID = "cust-456";
    private static final int RISK_SCORE = 75;
    private static final List<String> RULES = List.of("RULE_1", "RULE_2");

    @Test
    @DisplayName("approve() should return FraudResult with action APPROVE")
    void testApprove() {
        FraudResult result = FraudResult.approve(PAYMENT_ID, AMOUNT, CUSTOMER_ID, RISK_SCORE, RULES);

        assertEquals("APPROVE", result.action());
        assertEquals(PAYMENT_ID, result.paymentId());
        assertEquals(AMOUNT, result.amount());
        assertEquals(CUSTOMER_ID, result.customerId());
        assertEquals(RISK_SCORE, result.riskScore());
        assertEquals(RULES, result.triggeredRules());
        assertTrue(result instanceof FraudResult.Approve);
    }

    @Test
    @DisplayName("review() should return FraudResult with action REVIEW and reason")
    void testReview() {
        String reason = "Suspicious amount";

        FraudResult result = FraudResult.review(PAYMENT_ID, AMOUNT, CUSTOMER_ID, RISK_SCORE, reason, RULES);

        assertEquals("REVIEW", result.action());
        assertEquals(PAYMENT_ID, result.paymentId());
        assertEquals(AMOUNT, result.amount());
        assertEquals(CUSTOMER_ID, result.customerId());
        assertEquals(RISK_SCORE, result.riskScore());
        assertEquals(reason, ((FraudResult.Review) result).reason());
        assertEquals(RULES, result.triggeredRules());
        assertTrue(result instanceof FraudResult.Review);
    }

    @Test
    @DisplayName("reject() should return FraudResult with action REJECT and reason")
    void testReject() {
        String reason = "Known fraud pattern";

        FraudResult result = FraudResult.reject(PAYMENT_ID, AMOUNT, CUSTOMER_ID, RISK_SCORE, reason, RULES);

        assertEquals("REJECT", result.action());
        assertEquals(PAYMENT_ID, result.paymentId());
        assertEquals(AMOUNT, result.amount());
        assertEquals(CUSTOMER_ID, result.customerId());
        assertEquals(RISK_SCORE, result.riskScore());
        assertEquals(reason, ((FraudResult.Reject) result).reason());
        assertEquals(RULES, result.triggeredRules());
        assertTrue(result instanceof FraudResult.Reject);
    }

    @Test
    @DisplayName("Approve record fields should be accessible")
    void testApproveRecordFields() {
        FraudResult.Approve approve = new FraudResult.Approve(PAYMENT_ID, AMOUNT, CUSTOMER_ID, RISK_SCORE, RULES);

        assertEquals("APPROVE", approve.action());
        assertEquals(PAYMENT_ID, approve.paymentId());
        assertEquals(AMOUNT, approve.amount());
        assertEquals(CUSTOMER_ID, approve.customerId());
        assertEquals(RISK_SCORE, approve.riskScore());
        assertEquals(RULES, approve.triggeredRules());
    }

    @Test
    @DisplayName("Review record fields should be accessible including reason")
    void testReviewRecordFields() {
        String reason = "High amount";

        FraudResult.Review review = new FraudResult.Review(PAYMENT_ID, AMOUNT, CUSTOMER_ID, RISK_SCORE, reason, RULES);

        assertEquals("REVIEW", review.action());
        assertEquals(PAYMENT_ID, review.paymentId());
        assertEquals(AMOUNT, review.amount());
        assertEquals(CUSTOMER_ID, review.customerId());
        assertEquals(RISK_SCORE, review.riskScore());
        assertEquals(reason, review.reason());
        assertEquals(RULES, review.triggeredRules());
    }

    @Test
    @DisplayName("Reject record fields should be accessible including reason")
    void testRejectRecordFields() {
        String reason = "Blocked country";

        FraudResult.Reject reject = new FraudResult.Reject(PAYMENT_ID, AMOUNT, CUSTOMER_ID, RISK_SCORE, reason, RULES);

        assertEquals("REJECT", reject.action());
        assertEquals(PAYMENT_ID, reject.paymentId());
        assertEquals(AMOUNT, reject.amount());
        assertEquals(CUSTOMER_ID, reject.customerId());
        assertEquals(RISK_SCORE, reject.riskScore());
        assertEquals(reason, reject.reason());
        assertEquals(RULES, reject.triggeredRules());
    }
}
