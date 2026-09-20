package com.poc.processor.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FraudRuleTest {

    @Test
    void values_hasSixConstants() {
        assertEquals(6, FraudRule.values().length);
    }

    @Test
    void highAmount_ruleName() {
        assertEquals("HIGH_AMOUNT", FraudRule.HIGH_AMOUNT.getRuleName());
    }

    @Test
    void highRiskCountry_ruleName() {
        assertEquals("HIGH_RISK_COUNTRY", FraudRule.HIGH_RISK_COUNTRY.getRuleName());
    }

    @Test
    void unusualHour_ruleName() {
        assertEquals("UNUSUAL_HOUR", FraudRule.UNUSUAL_HOUR.getRuleName());
    }

    @Test
    void rapidRetry_ruleName() {
        assertEquals("RAPID_RETRY", FraudRule.RAPID_RETRY.getRuleName());
    }

    @Test
    void newPaymentMethod_ruleName() {
        assertEquals("NEW_PAYMENT_METHOD", FraudRule.NEW_PAYMENT_METHOD.getRuleName());
    }

    @Test
    void highRiskTier_ruleName() {
        assertEquals("HIGH_RISK_TIER", FraudRule.HIGH_RISK_TIER.getRuleName());
    }

    @Test
    void valueOf_validName() {
        assertEquals(FraudRule.HIGH_AMOUNT, FraudRule.valueOf("HIGH_AMOUNT"));
        assertEquals(FraudRule.HIGH_RISK_COUNTRY, FraudRule.valueOf("HIGH_RISK_COUNTRY"));
        assertEquals(FraudRule.UNUSUAL_HOUR, FraudRule.valueOf("UNUSUAL_HOUR"));
        assertEquals(FraudRule.RAPID_RETRY, FraudRule.valueOf("RAPID_RETRY"));
        assertEquals(FraudRule.NEW_PAYMENT_METHOD, FraudRule.valueOf("NEW_PAYMENT_METHOD"));
        assertEquals(FraudRule.HIGH_RISK_TIER, FraudRule.valueOf("HIGH_RISK_TIER"));
    }

    @Test
    void valueOf_invalidName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> FraudRule.valueOf("TYPO"));
    }
}
