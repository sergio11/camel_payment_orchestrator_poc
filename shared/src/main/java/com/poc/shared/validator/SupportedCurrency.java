package com.poc.shared.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedCurrencyValidator.class)
@Documented
public @interface SupportedCurrency {
    String message() default "Unsupported currency";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
