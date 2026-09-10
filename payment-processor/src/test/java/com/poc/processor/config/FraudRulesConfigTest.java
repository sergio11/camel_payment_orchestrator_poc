package com.poc.processor.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FraudRulesConfigTest {

    private FraudRulesConfig createConfig() throws Exception {
        FraudRulesConfig config = new FraudRulesConfig();
        setField(config, "highAmountThreshold", new BigDecimal("15000"));
        setField(config, "highRiskCountries", List.of("XX", "YY", "ZZ"));
        setField(config, "rapidRetryThreshold", 3);
        setField(config, "newMethodDaysThreshold", 30);
        setField(config, "unusualHourStart", 2);
        setField(config, "unusualHourEnd", 5);
        setField(config, "maxRiskScore", 100);
        setField(config, "riskScoreThresholdHigh", 80);
        setField(config, "riskScoreThresholdMedium", 50);
        setField(config, "cbrHighAmountThreshold", new BigDecimal("10000"));
        setField(config, "cbrWalletAmountThreshold", new BigDecimal("5000"));
        return config;
    }

    private void setField(FraudRulesConfig config, String fieldName, Object value) throws Exception {
        Field field = FraudRulesConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(config, value);
    }

    @Test
    @DisplayName("highAmountThreshold returns 15000")
    void highAmountThreshold() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(0, new BigDecimal("15000").compareTo(config.highAmountThreshold()));
    }

    @Test
    @DisplayName("highRiskCountries returns list containing XX, YY, ZZ")
    void highRiskCountries() throws Exception {
        FraudRulesConfig config = createConfig();
        List<String> countries = config.highRiskCountries();
        assertNotNull(countries);
        assertEquals(3, countries.size());
        assertTrue(countries.contains("XX"));
        assertTrue(countries.contains("YY"));
        assertTrue(countries.contains("ZZ"));
    }

    @Test
    @DisplayName("rapidRetryThreshold returns 3")
    void rapidRetryThreshold() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(3, config.rapidRetryThreshold());
    }

    @Test
    @DisplayName("newMethodDaysThreshold returns 30")
    void newMethodDaysThreshold() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(30, config.newMethodDaysThreshold());
    }

    @Test
    @DisplayName("unusualHourStart returns 2")
    void unusualHourStart() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(2, config.unusualHourStart());
    }

    @Test
    @DisplayName("unusualHourEnd returns 5")
    void unusualHourEnd() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(5, config.unusualHourEnd());
    }

    @Test
    @DisplayName("maxRiskScore returns 100")
    void maxRiskScore() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(100, config.maxRiskScore());
    }

    @Test
    @DisplayName("riskScoreThresholdHigh returns 80")
    void riskScoreThresholdHigh() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(80, config.riskScoreThresholdHigh());
    }

    @Test
    @DisplayName("riskScoreThresholdMedium returns 50")
    void riskScoreThresholdMedium() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(50, config.riskScoreThresholdMedium());
    }

    @Test
    @DisplayName("cbrHighAmountThreshold returns 10000")
    void cbrHighAmountThreshold() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(0, new BigDecimal("10000").compareTo(config.cbrHighAmountThreshold()));
    }

    @Test
    @DisplayName("cbrWalletAmountThreshold returns 5000")
    void cbrWalletAmountThreshold() throws Exception {
        FraudRulesConfig config = createConfig();
        assertEquals(0, new BigDecimal("5000").compareTo(config.cbrWalletAmountThreshold()));
    }

    @Test
    @DisplayName("validate with valid config does not throw")
    void validate_validConfig_doesNotThrow() throws Exception {
        FraudRulesConfig config = createConfig();
        assertDoesNotThrow(config::validate);
    }

    @Test
    @DisplayName("validate throws when unusualHourStart < 0")
    void validate_unusualHourStartBelowZero_throws() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "unusualHourStart", -1);
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    @DisplayName("validate throws when unusualHourStart > 23")
    void validate_unusualHourStartAbove23_throws() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "unusualHourStart", 24);
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    @DisplayName("validate throws when unusualHourEnd < 0")
    void validate_unusualHourEndBelowZero_throws() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "unusualHourEnd", -1);
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    @DisplayName("validate throws when unusualHourEnd > 23")
    void validate_unusualHourEndAbove23_throws() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "unusualHourEnd", 24);
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    @DisplayName("validate throws when riskScoreThresholdHigh <= riskScoreThresholdMedium")
    void validate_riskScoreHighNotGreaterThanMedium_throws() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "riskScoreThresholdHigh", 50);
        setField(config, "riskScoreThresholdMedium", 50);
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    @DisplayName("validate throws when cbrHighAmountThreshold <= cbrWalletAmountThreshold")
    void validate_cbrHighNotGreaterThanWallet_throws() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "cbrHighAmountThreshold", new BigDecimal("5000"));
        setField(config, "cbrWalletAmountThreshold", new BigDecimal("5000"));
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    @DisplayName("highRiskCountries returns empty list when field is null")
    void highRiskCountries_nullField_returnsEmptyList() throws Exception {
        FraudRulesConfig config = createConfig();
        setField(config, "highRiskCountries", null);
        List<String> countries = config.highRiskCountries();
        assertNotNull(countries);
        assertTrue(countries.isEmpty());
    }
}
