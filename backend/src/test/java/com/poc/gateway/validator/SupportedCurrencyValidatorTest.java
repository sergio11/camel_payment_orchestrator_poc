package com.poc.gateway.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportedCurrencyValidatorTest {

    private SupportedCurrencyValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new SupportedCurrencyValidator();
        context = mock(ConstraintValidatorContext.class);
        validator.initialize(null);
    }

    @Test
    void testValidCurrencies() {
        assertTrue(validator.isValid("USD", context));
        assertTrue(validator.isValid("EUR", context));
        assertTrue(validator.isValid("GBP", context));
        assertTrue(validator.isValid("MXN", context));
        assertTrue(validator.isValid("JPY", context));
    }

    @Test
    void testInvalidCurrencies() {
        assertFalse(validator.isValid("BTC", context));
        assertFalse(validator.isValid("XYZ", context));
        assertFalse(validator.isValid("AA", context));
    }

    @Test
    void testNullIsValid() {
        assertTrue(validator.isValid(null, context));
    }
}
