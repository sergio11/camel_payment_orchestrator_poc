package com.poc.shared.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {
    private static final Set<String> ALLOWED = Set.of("USD", "EUR", "GBP", "MXN", "JPY");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ALLOWED.contains(value);
    }
}
