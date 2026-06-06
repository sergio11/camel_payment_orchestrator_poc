package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record PaymentRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be >= 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must be <= 999999.99")
    @Digits(integer = 6, fraction = 2, message = "Amount max 2 decimal places")
    BigDecimal amount,

    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    String currency,

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID max 50 characters")
    String customerId,

    @NotNull(message = "Payment method is required")
    @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|CRYPTO", message = "Invalid payment method")
    String paymentMethod,

    @Size(max = 2, message = "Country code max 2 characters (ISO 3166-1 alpha-2)")
    String country,

    Map<String, Object> metadata
) {}