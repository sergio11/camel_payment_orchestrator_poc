package com.poc.gateway.exception;

import com.poc.gateway.domain.exception.PaymentNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class MarkerBasedClassifier implements ExceptionClassifier {

    private static final List<String> BAD_REQUEST_MARKERS = List.of(
        "JsonParseException",
        "JsonMappingException",
        "IllegalArgumentException",
        "NumberFormatException",
        "DateTimeParseException"
    );

    private static final List<String> CONSTRAINT_MARKERS = List.of(
        "duplicate",
        "unique",
        "constraint",
        "uq_",
        "uq "
    );

    @Override
    public ExceptionCategory classify(Throwable throwable) {
        if (throwable instanceof PaymentNotFoundException) {
            return ExceptionCategory.NOT_FOUND;
        }

        String name = throwable.getClass().getSimpleName();

        if (BAD_REQUEST_MARKERS.stream().anyMatch(name::contains)) {
            return ExceptionCategory.BAD_REQUEST;
        }

        if (throwable.getMessage() != null) {
            String msg = throwable.getMessage().toLowerCase();
            if (CONSTRAINT_MARKERS.stream().anyMatch(msg::contains)) {
                return ExceptionCategory.CONFLICT;
            }
        }

        if (CONSTRAINT_MARKERS.stream().anyMatch(name.toLowerCase()::contains)) {
            return ExceptionCategory.CONFLICT;
        }

        return ExceptionCategory.INTERNAL;
    }
}
