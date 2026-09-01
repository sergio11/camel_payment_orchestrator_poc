package com.poc.shared.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupportedCurrencyValidatorTest {

    private SupportedCurrencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SupportedCurrencyValidator();
    }

    @Test
    @DisplayName("initialize() should not throw exceptions")
    void testInitialize() {
        assertDoesNotThrow(() -> validator.initialize(null));
    }

    @Test
    @DisplayName("isValid() with USD should return true")
    void testValidUsd() {
        assertTrue(validator.isValid("USD", null));
    }

    @Test
    @DisplayName("isValid() with EUR should return true")
    void testValidEur() {
        assertTrue(validator.isValid("EUR", null));
    }

    @Test
    @DisplayName("isValid() with GBP should return true")
    void testValidGbp() {
        assertTrue(validator.isValid("GBP", null));
    }

    @Test
    @DisplayName("isValid() with MXN should return true")
    void testValidMxn() {
        assertTrue(validator.isValid("MXN", null));
    }

    @Test
    @DisplayName("isValid() with JPY should return true")
    void testValidJpy() {
        assertTrue(validator.isValid("JPY", null));
    }

    @Test
    @DisplayName("isValid() with BTC should return false")
    void testInvalidBtc() {
        assertFalse(validator.isValid("BTC", null));
    }

    @Test
    @DisplayName("isValid() with null should return true (optional field)")
    void testValidNull() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    @DisplayName("isValid() with empty string should return false (not a valid currency)")
    void testValidEmptyString() {
        assertFalse(validator.isValid("", null));
    }

    @Test
    @DisplayName("isValid() with invalid currency should return false")
    void testInvalidCurrency() {
        assertFalse(validator.isValid("DOGE", null));
    }

    @Test
    @DisplayName("isValid() with lowercase should return false")
    void testLowercaseCurrency() {
        assertFalse(validator.isValid("usd", null));
    }
}
