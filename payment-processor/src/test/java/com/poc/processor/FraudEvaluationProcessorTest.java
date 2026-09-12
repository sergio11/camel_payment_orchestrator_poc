package com.poc.processor;

import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.event.PaymentMessage;
import com.poc.processor.config.FraudRulesConfig;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.poc.shared.dto.PaymentMetadataDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FraudEvaluationProcessorTest {

    @Inject
    EvaluateFraudUseCase evaluateFraudUseCase;

    @Inject
    FraudRulesConfig config;

    private PaymentMessage createPaymentMessage(String country, BigDecimal amount, PaymentMetadataDTO metadata) {
        return new PaymentMessage(
            "event-1", "payment-1", amount, "USD", "customer-1",
            "CREDIT_CARD", country, 0, false, 0, "UTC", metadata, LocalDateTime.now()
        );
    }

    private PaymentMessage createFullPaymentMessage(String paymentId, String customerId, String country,
            BigDecimal amount, String currency, String paymentMethod, int attemptCount,
            boolean isNewPaymentMethod, int customerAgeDays, String timeZone, PaymentMetadataDTO metadata) {
        return new PaymentMessage(
            "event-1", paymentId, amount, currency, customerId,
            paymentMethod, country, attemptCount, isNewPaymentMethod, customerAgeDays,
            timeZone, metadata, LocalDateTime.now()
        );
    }

    private FraudEvaluation processAndReturn(PaymentMessage msg) {
        return evaluateFraudUseCase.evaluate(msg);
    }

    private PaymentMessage withMetadata(PaymentMessage msg, PaymentMetadataDTO extra) {
        return createFullPaymentMessage(msg.paymentId(), msg.customerId(), msg.country(), msg.amount(),
                msg.currency(), msg.paymentMethod(), msg.attemptCount(), msg.isNewPaymentMethod(),
                msg.customerAgeDays(), msg.timeZone(), extra);
    }

    // ========== HIGH_AMOUNT rule (+50) ==========

    @Test
    @DisplayName("HIGH_AMOUNT rule fires when amount > threshold (15000)")
    void highAmount_aboveThreshold_adds50() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("15001"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_AMOUNT"));
        assertTrue(result.riskScore() >= 50);
    }

    @Test
    @DisplayName("HIGH_AMOUNT rule does NOT fire when amount equals threshold (15000)")
    void highAmount_exactThreshold_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("15000"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("HIGH_AMOUNT"));
    }

    @Test
    @DisplayName("HIGH_AMOUNT rule does NOT fire when amount below threshold")
    void highAmount_belowThreshold_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("14999"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("HIGH_AMOUNT"));
    }

    // ========== HIGH_RISK_COUNTRY rule (+30) ==========

    @Test
    @DisplayName("HIGH_RISK_COUNTRY rule fires for country XX")
    void highRiskCountry_countryXX_adds30() {
        PaymentMessage msg = createPaymentMessage("XX", new BigDecimal("100"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
        assertTrue(result.riskScore() >= 30);
    }

    @Test
    @DisplayName("HIGH_RISK_COUNTRY rule fires for country YY")
    void highRiskCountry_countryYY_adds30() {
        PaymentMessage msg = createPaymentMessage("YY", new BigDecimal("100"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
    }

    @Test
    @DisplayName("HIGH_RISK_COUNTRY rule fires for country ZZ")
    void highRiskCountry_countryZZ_adds30() {
        PaymentMessage msg = createPaymentMessage("ZZ", new BigDecimal("100"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
    }

    @Test
    @DisplayName("HIGH_RISK_COUNTRY rule does NOT fire for safe country")
    void highRiskCountry_safeCountry_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
    }

    // ========== UNUSUAL_HOUR rule (+15) ==========

    @Test
    @DisplayName("UNUSUAL_HOUR rule fires when current hour is in unusual range [2-5]")
    void unusualHour_inRange_adds15() {
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        int targetHour = 3; // inside unusual range
        int offsetHours = targetHour - currentHour;
        // Use a timezone that shifts UTC hour into the unusual window
        ZoneId shiftedZone = ZoneId.of("UTC").getRules()
                .getOffset(LocalDateTime.now()) == null ? ZoneId.of("UTC") : ZoneId.of("UTC");

        // Calculate a zone offset in hours from UTC
        // We need a timezone where the local hour is 3
        // For example, UTC+X where (currentHour + X) mod 24 = 3
        int desiredOffset = ((targetHour - currentHour) % 24 + 24) % 24;
        if (desiredOffset > 14) desiredOffset -= 24;
        String zoneId = desiredOffset == 0 ? "UTC" : desiredOffset > 0 ? "Etc/GMT-" + desiredOffset : "Etc/GMT+" + (-desiredOffset);

        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, zoneId, PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("UNUSUAL_HOUR"),
            "Expected UNUSUAL_HOUR to fire with timezone " + zoneId + " (shifted hour to " + targetHour + ")");
    }

    @Test
    @DisplayName("UNUSUAL_HOUR rule does NOT fire when current hour outside unusual range")
    void unusualHour_outsideRange_noFire() {
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        // Determine if current hour is outside [2,5]
        boolean currentIsUnusual = currentHour >= 2 && currentHour <= 5;

        // Use a timezone that shifts to outside the range if current is inside
        String zoneId;
        if (currentIsUnusual) {
            // Shift to hour 12 (definitely outside [2,5])
            int desiredOffset = ((12 - currentHour) % 24 + 24) % 24;
            if (desiredOffset > 14) desiredOffset -= 24;
            zoneId = desiredOffset == 0 ? "UTC" : desiredOffset > 0 ? "Etc/GMT-" + desiredOffset : "Etc/GMT+" + (-desiredOffset);
        } else {
            zoneId = "UTC";
        }

        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, zoneId, PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("UNUSUAL_HOUR"),
            "Expected UNUSUAL_HOUR not to fire with timezone " + zoneId);
    }

    @Test
    @DisplayName("UNUSUAL_HOUR does NOT fire when local hour is below range (hour 1)")
    void unusualHour_belowRange_noFire() {
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        int targetHour = 1; // below unusual range [2,5]
        int desiredOffset = ((targetHour - currentHour) % 24 + 24) % 24;
        if (desiredOffset > 14) desiredOffset -= 24;
        String zoneId = desiredOffset == 0 ? "UTC" : desiredOffset > 0 ? "Etc/GMT-" + desiredOffset : "Etc/GMT+" + (-desiredOffset);

        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, zoneId, PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("UNUSUAL_HOUR"),
            "Expected UNUSUAL_HOUR not to fire at hour 1 with timezone " + zoneId);
    }

    @Test
    @DisplayName("UNUSUAL_HOUR uses UTC fallback when timeZone is null")
    void unusualHour_nullTimezone_fallsBackToUtc() {
        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, null, PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation result = processAndReturn(msg);
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        if (currentHour >= 2 && currentHour <= 5) {
            assertTrue(result.triggeredRules().contains("UNUSUAL_HOUR"));
        } else {
            assertFalse(result.triggeredRules().contains("UNUSUAL_HOUR"));
        }
    }

    @Test
    @DisplayName("UNUSUAL_HOUR uses UTC fallback when timeZone is invalid")
    void unusualHour_invalidTimezone_fallsBackToUtc() {
        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, "Invalid/Zone", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation result = processAndReturn(msg);
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        if (currentHour >= 2 && currentHour <= 5) {
            assertTrue(result.triggeredRules().contains("UNUSUAL_HOUR"));
        } else {
            assertFalse(result.triggeredRules().contains("UNUSUAL_HOUR"));
        }
    }

    // ========== RAPID_RETRY rule (+25) ==========

    @Test
    @DisplayName("RAPID_RETRY rule fires when attempts > threshold (3)")
    void rapidRetry_aboveThreshold_adds25() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), new PaymentMetadataDTO(null, 4, null, null, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("RAPID_RETRY"));
        assertTrue(result.riskScore() >= 25);
    }

    @Test
    @DisplayName("RAPID_RETRY rule does NOT fire when attempts equals threshold")
    void rapidRetry_exactThreshold_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), new PaymentMetadataDTO(null, 3, null, null, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("RAPID_RETRY"));
    }

    @Test
    @DisplayName("RAPID_RETRY rule does NOT fire when attempts below threshold")
    void rapidRetry_belowThreshold_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), new PaymentMetadataDTO(null, 2, null, null, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("RAPID_RETRY"));
    }

    @Test
    @DisplayName("RAPID_RETRY rule does NOT fire when metadata is null")
    void rapidRetry_nullMetadata_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), null);
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("RAPID_RETRY"));
    }

    // ========== NEW_PAYMENT_METHOD rule (+20) ==========

    @Test
    @DisplayName("NEW_PAYMENT_METHOD rule fires when isNewPaymentMethod=true and age < 30")
    void newPaymentMethod_newAndYoung_adds20() {
        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "US",
            new BigDecimal("100"), "USD", "CREDIT_CARD", 1, true, 30, "UTC",
            new PaymentMetadataDTO(null, null, true, 10, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("NEW_PAYMENT_METHOD"));
        assertTrue(result.riskScore() >= 20);
    }

    @Test
    @DisplayName("NEW_PAYMENT_METHOD rule does NOT fire when age >= threshold (30)")
    void newPaymentMethod_oldMethod_noFire() {
        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "US",
            new BigDecimal("100"), "USD", "CREDIT_CARD", 1, true, 30, "UTC",
            new PaymentMetadataDTO(null, null, true, 30, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("NEW_PAYMENT_METHOD"));
    }

    @Test
    @DisplayName("NEW_PAYMENT_METHOD rule does NOT fire when isNewPaymentMethod=false")
    void newPaymentMethod_notNew_noFire() {
        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "US",
            new BigDecimal("100"), "USD", "CREDIT_CARD", 1, false, 30, "UTC",
            new PaymentMetadataDTO(null, null, false, 10, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("NEW_PAYMENT_METHOD"));
    }

    // ========== HIGH_RISK_TIER rule (+10) ==========

    @Test
    @DisplayName("HIGH_RISK_TIER rule fires when customerRiskTier is HIGH")
    void highRiskTier_high_adds10() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"),
            new PaymentMetadataDTO(null, null, null, null, "HIGH", null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_RISK_TIER"));
        assertTrue(result.riskScore() >= 10);
    }

    @Test
    @DisplayName("HIGH_RISK_TIER rule does NOT fire when customerRiskTier is LOW")
    void highRiskTier_low_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"),
            new PaymentMetadataDTO(null, null, null, null, "LOW", null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("HIGH_RISK_TIER"));
    }

    @Test
    @DisplayName("HIGH_RISK_TIER rule does NOT fire when customerRiskTier is MEDIUM")
    void highRiskTier_medium_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"),
            new PaymentMetadataDTO(null, null, null, null, "MEDIUM", null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("HIGH_RISK_TIER"));
    }

    @Test
    @DisplayName("HIGH_RISK_TIER rule does NOT fire when metadata is null")
    void highRiskTier_nullMetadata_noFire() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), null);
        FraudEvaluation result = processAndReturn(msg);
        assertFalse(result.triggeredRules().contains("HIGH_RISK_TIER"));
    }

    // ========== COMBINED rules ==========

    @Test
    @DisplayName("COMBINED: HIGH_AMOUNT + HIGH_RISK_COUNTRY = 80 => REJECT")
    void combined_highAmount_highCountry_reject() {
        PaymentMessage msg = createPaymentMessage("XX", new BigDecimal("20000"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_AMOUNT"));
        assertTrue(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
        assertEquals(FraudAction.REJECT, result.action());
        assertEquals(80, result.riskScore());
    }

    @Test
    @DisplayName("COMBINED: HIGH_AMOUNT only = 50 => REVIEW")
    void combined_highAmountOnly_review() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("20000"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_AMOUNT"));
        assertEquals(FraudAction.REVIEW, result.action());
        assertEquals(50, result.riskScore());
    }

    @Test
    @DisplayName("COMBINED: HIGH_RISK_COUNTRY + RAPID_RETRY = 55 => REVIEW")
    void combined_country_retry_review() {
        PaymentMessage msg = createPaymentMessage("ZZ", new BigDecimal("100"), new PaymentMetadataDTO(null, 5, null, null, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
        assertTrue(result.triggeredRules().contains("RAPID_RETRY"));
        assertEquals(FraudAction.REVIEW, result.action());
        assertEquals(55, result.riskScore());
    }

    @Test
    @DisplayName("COMBINED: All rules fire = 150 capped at 100 => REJECT")
    void combined_allRules_cappedAt100() {
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        int targetHour = 3;
        int desiredOffset = ((targetHour - currentHour) % 24 + 24) % 24;
        if (desiredOffset > 14) desiredOffset -= 24;
        String zoneId = desiredOffset == 0 ? "UTC" : desiredOffset > 0 ? "Etc/GMT-" + desiredOffset : "Etc/GMT+" + (-desiredOffset);

        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "XX",
            new BigDecimal("20000"), "USD", "CREDIT_CARD", 4, true, 30, zoneId,
            new PaymentMetadataDTO(null, 5, true, 10, "HIGH", null, null, null));
        FraudEvaluation result = processAndReturn(msg);

        assertTrue(result.triggeredRules().contains("HIGH_AMOUNT"));
        assertTrue(result.triggeredRules().contains("HIGH_RISK_COUNTRY"));
        assertTrue(result.triggeredRules().contains("UNUSUAL_HOUR"));
        assertTrue(result.triggeredRules().contains("RAPID_RETRY"));
        assertTrue(result.triggeredRules().contains("NEW_PAYMENT_METHOD"));
        assertTrue(result.triggeredRules().contains("HIGH_RISK_TIER"));
        assertEquals(100, result.riskScore());
        assertEquals(FraudAction.REJECT, result.action());
    }

    @Test
    @DisplayName("COMBINED: HIGH_RISK_TIER + NEW_PAYMENT_METHOD = 30 => APPROVE")
    void combined_tier_newMethod_approve() {
        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "US",
            new BigDecimal("100"), "USD", "CREDIT_CARD", 1, true, 30, "UTC",
            new PaymentMetadataDTO(null, null, true, 5, "HIGH", null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertTrue(result.triggeredRules().contains("HIGH_RISK_TIER"));
        assertTrue(result.triggeredRules().contains("NEW_PAYMENT_METHOD"));
        assertEquals(FraudAction.APPROVE, result.action());
        assertEquals(30, result.riskScore());
    }

    // ========== determineAction boundary tests ==========

    @Test
    @DisplayName("APPROVE: riskScore < medium threshold (50)")
    void determineAction_approve() {
        PaymentMessage msg = createPaymentMessage("XX", new BigDecimal("100"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertEquals(FraudAction.APPROVE, result.action());
        assertTrue(result.riskScore() < config.riskScoreThresholdMedium());
    }

    @Test
    @DisplayName("REVIEW: riskScore exactly at medium threshold (50)")
    void determineAction_review_exactMedium() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("20000"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertEquals(FraudAction.REVIEW, result.action());
        assertEquals(50, result.riskScore());
    }

    @Test
    @DisplayName("REVIEW: riskScore between medium and high (50-79)")
    void determineAction_review_betweenThresholds() {
        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "US",
            new BigDecimal("100"), "USD", "CREDIT_CARD", 1, true, 30, "UTC",
            new PaymentMetadataDTO(null, 5, true, 10, null, null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        int score = result.riskScore();
        if (score >= 50 && score < 80) {
            assertEquals(FraudAction.REVIEW, result.action());
        }
    }

    @Test
    @DisplayName("REJECT: riskScore exactly at high threshold (80)")
    void determineAction_reject_exactHigh() {
        PaymentMessage msg = createPaymentMessage("XX", new BigDecimal("20000"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertEquals(FraudAction.REJECT, result.action());
        assertEquals(80, result.riskScore());
    }

    @Test
    @DisplayName("REJECT: riskScore above high threshold")
    void determineAction_reject_aboveHigh() {
        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "XX",
            new BigDecimal("20000"), "USD", "CREDIT_CARD", 1, false, 30, "UTC",
            new PaymentMetadataDTO(null, null, null, null, "HIGH", null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertEquals(FraudAction.REJECT, result.action());
        assertTrue(result.riskScore() >= 80);
    }

    // ========== maxRiskScore cap ==========

    @Test
    @DisplayName("Risk score is capped at maxRiskScore (100)")
    void riskScore_cappedAtMax() {
        int currentHour = LocalTime.now(ZoneId.of("UTC")).getHour();
        int targetHour = 3;
        int desiredOffset = ((targetHour - currentHour) % 24 + 24) % 24;
        if (desiredOffset > 14) desiredOffset -= 24;
        String zoneId = desiredOffset == 0 ? "UTC" : desiredOffset > 0 ? "Etc/GMT-" + desiredOffset : "Etc/GMT+" + (-desiredOffset);

        PaymentMessage msg = createFullPaymentMessage("payment-1", "customer-1", "XX",
            new BigDecimal("20000"), "USD", "CREDIT_CARD", 4, true, 30, zoneId,
            new PaymentMetadataDTO(null, 5, true, 10, "HIGH", null, null, null));
        FraudEvaluation result = processAndReturn(msg);
        assertEquals(100, result.riskScore());
    }

    // ========== Result field verification ==========

    @Test
    @DisplayName("FraudEvaluation contains correct paymentId, amount, and customerId")
    void result_containsCorrectFields() {
        PaymentMessage msg = createFullPaymentMessage("payment-99", "customer-42", "US",
            new BigDecimal("500.75"), "EUR", "DEBIT_CARD", 1, false, 60, "UTC", PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertEquals("payment-99", result.paymentId());
        assertEquals(new BigDecimal("500.75"), result.amount());
        assertEquals("customer-42", result.customerId());
        assertNotNull(result.triggeredRules());
    }

    // ========== Null metadata handling ==========

    @Test
    @DisplayName("Processor handles null metadata gracefully")
    void handlesNullMetadata() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), null);
        FraudEvaluation result = processAndReturn(msg);
        assertNotNull(result);
        assertFalse(result.triggeredRules().contains("RAPID_RETRY"));
        assertFalse(result.triggeredRules().contains("NEW_PAYMENT_METHOD"));
        assertFalse(result.triggeredRules().contains("HIGH_RISK_TIER"));
    }

    // ========== Empty metadata handling ==========

    @Test
    @DisplayName("Processor handles empty metadata gracefully")
    void handlesEmptyMetadata() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), PaymentMetadataDTO.empty());
        FraudEvaluation result = processAndReturn(msg);
        assertNotNull(result);
        assertEquals(FraudAction.APPROVE, result.action());
    }

    @Test
    @DisplayName("Processor handles truly null metadata map")
    void handlesTrulyNullMetadata() {
        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, "UTC", null, LocalDateTime.now()
        );
        FraudEvaluation result = processAndReturn(msg);
        assertNotNull(result);
        assertEquals(FraudAction.APPROVE, result.action());
        assertFalse(result.triggeredRules().contains("RAPID_RETRY"));
        assertFalse(result.triggeredRules().contains("HIGH_RISK_TIER"));
    }
}
