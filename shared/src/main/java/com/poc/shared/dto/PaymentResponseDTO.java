package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponseDTO(
    @JsonProperty("id")
    String id,

    @JsonProperty("amount")
    BigDecimal amount,

    @JsonProperty("currency")
    String currency,

    @JsonProperty("customer_id")
    String customerId,

    @JsonProperty("payment_method")
    String paymentMethod,

    @JsonProperty("country")
    String country,

    @JsonProperty("status")
    String status,

    @JsonProperty("provider")
    String provider,

    @JsonProperty("failure_reason")
    String failureReason,

    @JsonProperty("metadata")
    PaymentMetadataDTO metadata,

    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime createdAt,

    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime updatedAt
) {}
