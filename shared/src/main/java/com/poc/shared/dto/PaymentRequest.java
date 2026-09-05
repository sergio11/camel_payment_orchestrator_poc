package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.poc.shared.validator.SupportedCurrency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record PaymentRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be >= 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must be <= 999999.99")
    BigDecimal amount,

    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    @SupportedCurrency
    String currency,

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID max 50 characters")
    String customerId,

    @NotNull(message = "Payment method is required")
    String paymentMethod,

    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be ISO 3166-1 alpha-2 (e.g. US)")
    String country,

    @Size(max = 20, message = "Metadata max 20 entries")
    Map<String, Object> metadata
) {}