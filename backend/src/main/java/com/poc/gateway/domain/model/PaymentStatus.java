package com.poc.gateway.domain.model;

import java.util.Optional;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    REJECTED,
    FAILED,
    REVIEW;

    private static final java.util.Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = java.util.Map.of(
        PENDING, Set.of(PROCESSING, FAILED, REJECTED),
        PROCESSING, Set.of(APPROVED, REJECTED, FAILED, REVIEW),
        REVIEW, Set.of(PROCESSING, APPROVED, REJECTED, FAILED),
        FAILED, Set.of(PENDING),
        APPROVED, Set.of(),
        REJECTED, Set.of()
    );

    public boolean canTransitionTo(PaymentStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

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
