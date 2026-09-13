package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poc.shared.validator.SupportedCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PaymentRequestDTO(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be >= 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must be <= 999999.99")
    @JsonProperty("amount")
    BigDecimal amount,

    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    @SupportedCurrency
    @JsonProperty("currency")
    String currency,

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID max 50 characters")
    @JsonProperty("customer_id")
    String customerId,

    @NotNull(message = "Payment method is required")
    @JsonProperty("payment_method")
    String paymentMethod,

    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be ISO 3166-1 alpha-2 (e.g. US)")
    @JsonProperty("country")
    String country,

    @Valid
    @JsonProperty("metadata")
    PaymentMetadataDTO metadata
) {}
