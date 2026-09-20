package com.poc.processor.application;

import com.poc.processor.config.FraudRulesConfig;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poc.processor.domain.exception.FraudEvaluationException;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FraudEvaluationServiceUnitTest {

    @Mock
    FraudRulesConfig config;

    @InjectMocks
    FraudEvaluationService service;

    @BeforeEach
    void setupConfigDefaults() {
        when(config.highAmountThreshold()).thenReturn(new BigDecimal("15000"));
        when(config.highRiskCountries()).thenReturn(List.of("XX", "YY", "ZZ", "WW"));
        when(config.unusualHourStart()).thenReturn(2);
        when(config.unusualHourEnd()).thenReturn(5);
        when(config.rapidRetryThreshold()).thenReturn(3);
        when(config.newMethodDaysThreshold()).thenReturn(30);
        when(config.maxRiskScore()).thenReturn(100);
        when(config.riskScoreThresholdHigh()).thenReturn(80);
        when(config.riskScoreThresholdMedium()).thenReturn(50);
    }

    @Test
    @DisplayName("low risk payment returns APPROVE")
    void evaluate_lowRisk_approve() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CREDIT_CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertEquals(FraudAction.APPROVE, eval.action());
        assertTrue(eval.riskScore() < 50);
    }

    @Test
    @DisplayName("high amount triggers HIGH_AMOUNT rule and +50 score")
    void evaluate_highAmount_ruleTriggered() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("20000"), "USD", "cust-1",
            "CREDIT_CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.triggeredRules().contains("HIGH_AMOUNT"));
        assertTrue(eval.riskScore() >= 50);
    }

    @Test
    @DisplayName("high risk country triggers HIGH_RISK_COUNTRY rule and +30 score")
    void evaluate_highRiskCountry_ruleTriggered() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CREDIT_CARD", "XX", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.triggeredRules().contains("HIGH_RISK_COUNTRY"));
        assertTrue(eval.riskScore() >= 30);
    }

    @Test
    @DisplayName("high amount + high risk country returns REJECT")
    void evaluate_highAmountHighCountry_reject() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("20000"), "USD", "cust-1",
            "CREDIT_CARD", "XX", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertEquals(FraudAction.REJECT, eval.action());
        assertTrue(eval.riskScore() >= 80);
    }

    @Test
    @DisplayName("risk score is capped at maxRiskScore")
    void evaluate_scoreCapped() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("20000"), "USD", "cust-1",
            "CARD", "XX", 5, true, 10, "UTC",
            new PaymentMetadataDTO(null, 5, true, 10, "HIGH", null, null, null),
            LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.riskScore() <= 100);
    }

    @Test
    @DisplayName("rapid retry triggers RAPID_RETRY rule")
    void evaluate_rapidRetry_ruleTriggered() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 5, false, 0, "UTC",
            new PaymentMetadataDTO(null, 5, false, 0, null, null, null, null),
            LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.triggeredRules().contains("RAPID_RETRY"));
    }

    @Test
    @DisplayName("new payment method triggers NEW_PAYMENT_METHOD rule")
    void evaluate_newPaymentMethod_ruleTriggered() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, true, 0, "UTC",
            new PaymentMetadataDTO(null, 0, true, 10, null, null, null, null),
            LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.triggeredRules().contains("NEW_PAYMENT_METHOD"));
    }

    @Test
    @DisplayName("high risk tier triggers HIGH_RISK_TIER rule")
    void evaluate_highRiskTier_ruleTriggered() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, "UTC",
            new PaymentMetadataDTO(null, 0, false, 0, "HIGH", null, null, null),
            LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.triggeredRules().contains("HIGH_RISK_TIER"));
    }

    @Test
    @DisplayName("evaluate throws FraudEvaluationException when config is null")
    void evaluate_configError_throwsFraudEvaluationException() {
        when(config.highAmountThreshold()).thenThrow(new RuntimeException("Config error"));

        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CREDIT_CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        assertThrows(FraudEvaluationException.class,
            () -> service.evaluate(msg));
    }

    @Test
    @DisplayName("no metadata still evaluates successfully")
    void evaluate_nullMetadata() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, "UTC",
            null, LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertNotNull(eval.action());
        assertTrue(eval.isApprove() || eval.isReview() || eval.isReject());
    }

    @Test
    @DisplayName("null country skips HIGH_RISK_COUNTRY rule")
    void evaluate_nullCountry_skipsCountryRule() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", null, 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertFalse(eval.triggeredRules().contains("HIGH_RISK_COUNTRY"));
    }

    @Test
    @DisplayName("null timeZone falls back to UTC")
    void evaluate_nullTimeZone_fallsBackToUtc() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, null,
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertNotNull(eval.action());
    }

    @Test
    @DisplayName("unknown timeZone falls back to UTC")
    void evaluate_unknownTimeZone_fallsBackToUtc() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, "Mars/Olympus",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertNotNull(eval.action());
    }

    @Test
    @DisplayName("full-day unusual-hour window always triggers UNUSUAL_HOUR rule")
    void evaluate_fullDayWindow_triggersUnusualHour() {
        when(config.unusualHourStart()).thenReturn(0);
        when(config.unusualHourEnd()).thenReturn(23);

        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertTrue(eval.triggeredRules().contains("UNUSUAL_HOUR"));
    }

    @Test
    @DisplayName("inverted unusual-hour window never triggers UNUSUAL_HOUR rule")
    void evaluate_invertedWindow_skipsUnusualHour() {
        when(config.unusualHourStart()).thenReturn(24);
        when(config.unusualHourEnd()).thenReturn(23);

        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        FraudEvaluation eval = service.evaluate(msg);

        assertFalse(eval.triggeredRules().contains("UNUSUAL_HOUR"));
    }
}
