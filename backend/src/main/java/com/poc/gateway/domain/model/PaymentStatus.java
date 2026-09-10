package com.poc.gateway.domain.model;

import java.util.Optional;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    REJECTED,
    FAILED,
    REVIEW;

    public static Optional<PaymentStatus> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
