package com.poc.processor.application;

import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.domain.exception.PaymentProcessingException;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PaymentProcessingServiceTest {

    @Inject
    PaymentProcessingService service;

    @Inject
    EnrichPaymentUseCase enrichPaymentUseCase;

    @Inject
    EvaluateFraudUseCase evaluateFraudUseCase;

    @Test
    @DisplayName("execute with low risk payment completes successfully")
    void execute_approve() {
        PaymentMessage msg = createLowRiskMessage();
        service.execute(msg);
        assertNotNull(msg, "Message should still be valid after processing");
        assertNotNull(msg.paymentId(), "PaymentId should be preserved");
    }

    @Test
    @DisplayName("execute with high risk payment completes successfully")
    void execute_reject() {
        PaymentMessage msg = createHighRiskMessage();
        service.execute(msg);
        assertNotNull(msg, "Message should still be valid after processing");
        assertNotNull(msg.paymentId(), "PaymentId should be preserved");
    }

    @Test
    @DisplayName("execute with medium risk payment completes successfully")
    void execute_review() {
        PaymentMessage msg = createMediumRiskMessage();
        service.execute(msg);
        assertNotNull(msg, "Message should still be valid after processing");
        assertNotNull(msg.paymentId(), "PaymentId should be preserved");
    }

    @Test
    @DisplayName("validatePaymentMessage passes for valid message")
    void validatePaymentMessage_valid() {
        PaymentMessage msg = createLowRiskMessage();
        assertDoesNotThrow(() -> PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null paymentId")
    void validatePaymentMessage_nullPaymentId() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", null, new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null amount")
    void validatePaymentMessage_nullAmount() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", null, "USD", "cust-1",
            "CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null currency")
    void validatePaymentMessage_nullCurrency() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), null, "cust-1",
            "CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null customerId")
    void validatePaymentMessage_nullCustomerId() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", null,
            "CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null paymentMethod")
    void validatePaymentMessage_nullPaymentMethod() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            null, "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("Enrichment produces non-null metadata")
    void enrichment_producesNonNullMetadata() {
        PaymentMessage msg = createLowRiskMessage();
        PaymentMessage enriched = enrichPaymentUseCase.enrich(msg);
        assertNotNull(enriched.metadata());
        assertNotNull(enriched.metadata().enrichedAt());
        assertNotNull(enriched.metadata().customerRiskTier());
        assertNotNull(enriched.metadata().velocityScore());
        assertNotNull(enriched.metadata().geoRiskScore());
    }

    @Test
    @DisplayName("Fraud evaluation returns valid action")
    void fraudEvaluation_returnsValidAction() {
        PaymentMessage msg = createLowRiskMessage();
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertNotNull(eval.action());
        assertTrue(eval.isApprove() || eval.isReview() || eval.isReject());
    }

    @Test
    @DisplayName("Fraud evaluation for low risk returns APPROVE")
    void fraudEvaluation_lowRisk_approve() {
        PaymentMessage msg = createLowRiskMessage();
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertEquals(FraudAction.APPROVE, eval.action());
    }

    @Test
    @DisplayName("Fraud evaluation for high risk returns REJECT")
    void fraudEvaluation_highRisk_reject() {
        PaymentMessage msg = createHighRiskMessage();
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertEquals(FraudAction.REJECT, eval.action());
    }

    @Test
    @DisplayName("Fraud evaluation for medium risk returns REVIEW")
    void fraudEvaluation_mediumRisk_review() {
        PaymentMessage msg = createMediumRiskMessage();
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertEquals(FraudAction.REVIEW, eval.action());
    }

    @Test
    @DisplayName("Enrichment preserves original fields")
    void enrichment_preservesOriginalFields() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100.00"), "EUR", "cust-1",
            "CARD", "DE", 3, true, 60, "UTC",
            new PaymentMetadataDTO("order-1", 2, false, 15, null, null, null, null),
            LocalDateTime.of(2025, 1, 15, 10, 30)
        );
        PaymentMessage enriched = enrichPaymentUseCase.enrich(msg);
        assertEquals("evt-1", enriched.eventId());
        assertEquals("pay-1", enriched.paymentId());
        assertEquals(new BigDecimal("100.00"), enriched.amount());
        assertEquals("EUR", enriched.currency());
        assertEquals("cust-1", enriched.customerId());
        assertEquals("CARD", enriched.paymentMethod());
        assertEquals("DE", enriched.country());
        assertEquals(3, enriched.attemptCount());
        assertTrue(enriched.isNewPaymentMethod());
        assertEquals(60, enriched.customerAgeDays());
        assertEquals("order-1", enriched.metadata().orderId());
    }

    @Test
    @DisplayName("Fraud evaluation with high amount triggers HIGH_AMOUNT rule")
    void fraudEvaluation_highAmountRule() {
        PaymentMessage msg = createHighRiskMessage();
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertTrue(eval.triggeredRules().contains("HIGH_AMOUNT"));
    }

    @Test
    @DisplayName("Fraud evaluation with high risk country triggers rule")
    void fraudEvaluation_highRiskCountryRule() {
        PaymentMessage msg = createHighRiskMessage();
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertTrue(eval.triggeredRules().contains("HIGH_RISK_COUNTRY"));
    }

    @Test
    @DisplayName("Fraud evaluation risk score is capped at max")
    void fraudEvaluation_riskScoreCapped() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("20000"), "USD", "cust-1",
            "CARD", "XX", 5, true, 10, "UTC",
            new PaymentMetadataDTO(null, 5, true, 10, "HIGH", null, null, null),
            LocalDateTime.now()
        );
        FraudEvaluation eval = evaluateFraudUseCase.evaluate(msg);
        assertTrue(eval.riskScore() <= 100);
    }

    private PaymentMessage createLowRiskMessage() {
        return new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    private PaymentMessage createHighRiskMessage() {
        return new PaymentMessage(
            "evt-1", "pay-2", new BigDecimal("20000.00"), "USD", "cust-2",
            "CREDIT_CARD", "XX", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    private PaymentMessage createMediumRiskMessage() {
        return new PaymentMessage(
            "evt-1", "pay-3", new BigDecimal("20000.00"), "USD", "cust-3",
            "CREDIT_CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }
}
