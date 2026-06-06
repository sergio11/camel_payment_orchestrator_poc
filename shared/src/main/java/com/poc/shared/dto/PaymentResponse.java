package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record PaymentResponse(
    String id,
    BigDecimal amount,
    String currency,
    String customerId,
    String paymentMethod,
    String country,
    String status,
    String provider,
    String failureReason,
    Map<String, Object> metadata,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime createdAt,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime updatedAt
) {}