package com.poc.processor.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FraudRulesConfigTest {

    @Inject
    FraudRulesConfig config;

    @Test
    @DisplayName("highAmountThreshold returns 15000")
    void highAmountThreshold() {
        assertEquals(0, new BigDecimal("15000").compareTo(config.highAmountThreshold()));
    }

    @Test
    @DisplayName("highRiskCountries returns list containing XX, YY, ZZ")
    void highRiskCountries() {
        List<String> countries = config.highRiskCountries();
        assertNotNull(countries);
        assertEquals(3, countries.size());
        assertTrue(countries.contains("XX"));
        assertTrue(countries.contains("YY"));
        assertTrue(countries.contains("ZZ"));
    }

    @Test
    @DisplayName("rapidRetryThreshold returns 3")
    void rapidRetryThreshold() {
        assertEquals(3, config.rapidRetryThreshold());
    }

    @Test
    @DisplayName("newMethodDaysThreshold returns 30")
    void newMethodDaysThreshold() {
        assertEquals(30, config.newMethodDaysThreshold());
    }

    @Test
    @DisplayName("unusualHourStart returns 2")
    void unusualHourStart() {
        assertEquals(2, config.unusualHourStart());
    }

    @Test
    @DisplayName("unusualHourEnd returns 5")
    void unusualHourEnd() {
        assertEquals(5, config.unusualHourEnd());
    }

    @Test
    @DisplayName("maxRiskScore returns 100")
    void maxRiskScore() {
        assertEquals(100, config.maxRiskScore());
    }

    @Test
    @DisplayName("riskScoreThresholdHigh returns 80")
    void riskScoreThresholdHigh() {
        assertEquals(80, config.riskScoreThresholdHigh());
    }

    @Test
    @DisplayName("riskScoreThresholdMedium returns 50")
    void riskScoreThresholdMedium() {
        assertEquals(50, config.riskScoreThresholdMedium());
    }

    @Test
    @DisplayName("cbrHighAmountThreshold returns 10000")
    void cbrHighAmountThreshold() {
        assertEquals(0, new BigDecimal("10000").compareTo(config.cbrHighAmountThreshold()));
    }

    @Test
    @DisplayName("cbrWalletAmountThreshold returns 5000")
    void cbrWalletAmountThreshold() {
        assertEquals(0, new BigDecimal("5000").compareTo(config.cbrWalletAmountThreshold()));
    }
}
