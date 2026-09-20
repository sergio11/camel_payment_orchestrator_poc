package com.poc.processor.domain;

import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import com.poc.processor.port.inbound.RouteFraudUseCase.FraudRoutingDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainLayerTest {

    // ========== FraudEvaluation ==========

    @Test
    @DisplayName("FraudEvaluation record construction with all fields")
    void fraudEvaluation_recordConstruction() {
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 75, FraudAction.REJECT, "High risk", List.of("HIGH_AMOUNT")
        );
        assertEquals("pay-1", eval.paymentId());
        assertEquals(new BigDecimal("100"), eval.amount());
        assertEquals("cust-1", eval.customerId());
        assertEquals(75, eval.riskScore());
        assertEquals(FraudAction.REJECT, eval.action());
        assertEquals("High risk", eval.reason());
        assertEquals(List.of("HIGH_AMOUNT"), eval.triggeredRules());
    }

    @Test
    @DisplayName("FraudEvaluation constants are correct")
    void fraudEvaluation_constants() {
        assertEquals(FraudAction.APPROVE, FraudAction.APPROVE);
        assertEquals(FraudAction.REVIEW, FraudAction.REVIEW);
        assertEquals(FraudAction.REJECT, FraudAction.REJECT);
    }

    @Test
    @DisplayName("FraudEvaluation.isApprove returns true for APPROVE action")
    void fraudEvaluation_isApprove() {
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 10, FraudAction.APPROVE, null, List.of()
        );
        assertTrue(eval.isApprove());
        assertFalse(eval.isReview());
        assertFalse(eval.isReject());
    }

    @Test
    @DisplayName("FraudEvaluation.isReview returns true for REVIEW action")
    void fraudEvaluation_isReview() {
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 60, FraudAction.REVIEW, "Medium risk", List.of()
        );
        assertFalse(eval.isApprove());
        assertTrue(eval.isReview());
        assertFalse(eval.isReject());
    }

    @Test
    @DisplayName("FraudEvaluation.isReject returns true for REJECT action")
    void fraudEvaluation_isReject() {
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 90, FraudAction.REJECT, "High risk", List.of()
        );
        assertFalse(eval.isApprove());
        assertFalse(eval.isReview());
        assertTrue(eval.isReject());
    }

    @Test
    @DisplayName("FraudEvaluation record equality")
    void fraudEvaluation_equality() {
        FraudEvaluation eval1 = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 75, FraudAction.REJECT, "reason", List.of("R1")
        );
        FraudEvaluation eval2 = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 75, FraudAction.REJECT, "reason", List.of("R1")
        );
        assertEquals(eval1, eval2);
    }

    @Test
    @DisplayName("FraudEvaluation record with null fields")
    void fraudEvaluation_nullFields() {
        FraudEvaluation eval = new FraudEvaluation(
            null, null, null, 0, null, null, null
        );
        assertNull(eval.paymentId());
        assertNull(eval.amount());
        assertNull(eval.customerId());
        assertEquals(0, eval.riskScore());
        assertNull(eval.action());
        assertNull(eval.reason());
        assertNull(eval.triggeredRules());
    }

    // ========== ProviderGatewayResult ==========

    @Test
    @DisplayName("ProviderGatewayResult.success factory method")
    void providerGatewayResult_success() {
        ProviderGatewayResult result = ProviderGatewayResult.success("stripe", "txn-123");
        assertEquals("stripe", result.providerId());
        assertEquals("txn-123", result.transactionId());
        assertTrue(result.success());
        assertNull(result.errorCode());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("ProviderGatewayResult.failure factory method")
    void providerGatewayResult_failure() {
        ProviderGatewayResult result = ProviderGatewayResult.failure("stripe", "CARD_DECLINED", "Card declined");
        assertEquals("stripe", result.providerId());
        assertNull(result.transactionId());
        assertFalse(result.success());
        assertEquals("CARD_DECLINED", result.errorCode());
        assertEquals("Card declined", result.errorMessage());
    }

    @Test
    @DisplayName("ProviderGatewayResult record construction with all fields")
    void providerGatewayResult_recordConstruction() {
        ProviderGatewayResult result = new ProviderGatewayResult(
            "paypal", "txn-456", true, null, null
        );
        assertEquals("paypal", result.providerId());
        assertEquals("txn-456", result.transactionId());
        assertTrue(result.success());
    }

    @Test
    @DisplayName("ProviderGatewayResult record equality")
    void providerGatewayResult_equality() {
        ProviderGatewayResult r1 = new ProviderGatewayResult("p1", "t1", true, null, null);
        ProviderGatewayResult r2 = new ProviderGatewayResult("p1", "t1", true, null, null);
        assertEquals(r1, r2);
    }

    // ========== PaymentContext ==========

    @Test
    @DisplayName("PaymentContext.from creates context from PaymentMessage")
    void paymentContext_from() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        PaymentContext ctx = PaymentContext.from(msg);
        assertEquals(msg, ctx.message());
        assertEquals("pay-1", ctx.originalPaymentId());
        assertEquals("evt-1", ctx.originalEventId());
        assertEquals("pay-1", ctx.correlationId());
    }

    @Test
    @DisplayName("PaymentContext record construction")
    void paymentContext_recordConstruction() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        PaymentContext ctx = new PaymentContext(msg, "pay-1", "evt-1", "kafka-key");
        assertEquals(msg, ctx.message());
        assertEquals("pay-1", ctx.originalPaymentId());
        assertEquals("evt-1", ctx.originalEventId());
        assertEquals("kafka-key", ctx.correlationId());
    }

    // ========== Exceptions ==========

    @Test
    @DisplayName("PaymentProcessingException with message")
    void paymentProcessingException_message() {
        var ex = new com.poc.processor.domain.exception.PaymentProcessingException("pay-1", "Processing failed");
        assertEquals("Processing failed", ex.getMessage());
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("PaymentProcessingException with cause")
    void paymentProcessingException_cause() {
        var cause = new RuntimeException("root cause");
        var ex = new com.poc.processor.domain.exception.PaymentProcessingException("pay-1", "Failed", cause);
        assertEquals("Failed", ex.getMessage());
        assertEquals("pay-1", ex.getPaymentId());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("FraudEvaluationException with message")
    void fraudEvaluationException_message() {
        var ex = new com.poc.processor.domain.exception.FraudEvaluationException("pay-1", "Evaluation failed");
        assertEquals("Evaluation failed", ex.getMessage());
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("FraudEvaluationException with cause")
    void fraudEvaluationException_cause() {
        var cause = new RuntimeException("root cause");
        var ex = new com.poc.processor.domain.exception.FraudEvaluationException("pay-1", "Failed", cause);
        assertEquals("Failed", ex.getMessage());
        assertEquals("pay-1", ex.getPaymentId());
        assertEquals(cause, ex.getCause());
    }

    // ========== ProcessorDomainException ==========

    @Test
    @DisplayName("ProcessorDomainException with message")
    void processorDomainException_message() {
        var ex = new com.poc.processor.domain.exception.ProcessorDomainException("pay-1", "Domain error");
        assertEquals("Domain error", ex.getMessage());
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("ProcessorDomainException with cause")
    void processorDomainException_cause() {
        var cause = new RuntimeException("root cause");
        var ex = new com.poc.processor.domain.exception.ProcessorDomainException("pay-1", "Failed", cause);
        assertEquals("Failed", ex.getMessage());
        assertEquals("pay-1", ex.getPaymentId());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("PaymentProcessingException extends ProcessorDomainException")
    void paymentProcessingException_extendsBase() {
        var ex = new com.poc.processor.domain.exception.PaymentProcessingException("pay-1", "error");
        assertInstanceOf(com.poc.processor.domain.exception.ProcessorDomainException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("FraudEvaluationException extends ProcessorDomainException")
    void fraudEvaluationException_extendsBase() {
        var ex = new com.poc.processor.domain.exception.FraudEvaluationException("pay-1", "error");
        assertInstanceOf(com.poc.processor.domain.exception.ProcessorDomainException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    // ========== FraudRule ==========

    @Test
    @DisplayName("FraudRule has all 6 constants")
    void fraudRule_allValues() {
        assertEquals(6, FraudRule.values().length);
    }

    @Test
    @DisplayName("FraudRule.getRuleName returns correct names")
    void fraudRule_getRuleName() {
        assertEquals("HIGH_AMOUNT", FraudRule.HIGH_AMOUNT.getRuleName());
        assertEquals("HIGH_RISK_COUNTRY", FraudRule.HIGH_RISK_COUNTRY.getRuleName());
        assertEquals("UNUSUAL_HOUR", FraudRule.UNUSUAL_HOUR.getRuleName());
        assertEquals("RAPID_RETRY", FraudRule.RAPID_RETRY.getRuleName());
        assertEquals("NEW_PAYMENT_METHOD", FraudRule.NEW_PAYMENT_METHOD.getRuleName());
        assertEquals("HIGH_RISK_TIER", FraudRule.HIGH_RISK_TIER.getRuleName());
    }

    // ========== FraudRoutingDecision ==========

    @Test
    @DisplayName("FraudRoutingDecision record construction")
    void fraudRoutingDecision_construction() {
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 30, FraudAction.APPROVE, "Low risk", List.of()
        );
        FraudRoutingDecision decision = new FraudRoutingDecision(FraudAction.APPROVE, "direct:fraud-check", eval);
        assertEquals(FraudAction.APPROVE, decision.action());
        assertEquals("direct:fraud-check", decision.routeTarget());
        assertEquals(eval, decision.evaluation());
    }

    @Test
    @DisplayName("FraudRoutingDecision record equality")
    void fraudRoutingDecision_equality() {
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("100"), "cust-1", 30, FraudAction.APPROVE, "Low risk", List.of()
        );
        FraudRoutingDecision d1 = new FraudRoutingDecision(FraudAction.APPROVE, "direct:fraud-check", eval);
        FraudRoutingDecision d2 = new FraudRoutingDecision(FraudAction.APPROVE, "direct:fraud-check", eval);
        assertEquals(d1, d2);
    }
}
