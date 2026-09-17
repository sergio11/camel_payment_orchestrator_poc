package com.poc.gateway.domain;

import com.poc.gateway.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Payment(
    UUID id,
    BigDecimal amount,
    String currency,
    String customerId,
    String paymentMethod,
    String country,
    PaymentStatus status,
    String provider,
    String failureReason,
    PaymentMetadata metadata,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static Payment create(
        BigDecimal amount,
        String currency,
        String customerId,
        String paymentMethod,
        String country,
        PaymentMetadata metadata
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new Payment(
            UUID.randomUUID(),
            amount,
            currency,
            customerId,
            paymentMethod,
            country,
            PaymentStatus.PENDING,
            null,
            null,
            metadata,
            now,
            now
        );
    }

    public Payment withStatus(PaymentStatus newStatus) {
        return new Payment(
            id, amount, currency, customerId, paymentMethod,
            country, newStatus, provider, failureReason,
            metadata, createdAt, LocalDateTime.now()
        );
    }

    public Payment withProvider(String provider) {
        return new Payment(
            id, amount, currency, customerId, paymentMethod,
            country, status, provider, failureReason,
            metadata, createdAt, LocalDateTime.now()
        );
    }

    public Payment withFailure(String failureReason) {
        return new Payment(
            id, amount, currency, customerId, paymentMethod,
            country, PaymentStatus.FAILED, provider, failureReason,
            metadata, createdAt, LocalDateTime.now()
        );
    }

    public Payment withUpdatedAt(LocalDateTime newUpdatedAt) {
        return new Payment(
            id, amount, currency, customerId, paymentMethod,
            country, status, provider, failureReason,
            metadata, createdAt, newUpdatedAt
        );
    }
}
