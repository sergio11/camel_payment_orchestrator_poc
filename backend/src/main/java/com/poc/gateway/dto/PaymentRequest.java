package com.poc.gateway.dto;

import com.poc.gateway.validator.SupportedCurrency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public record PaymentRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be >= 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must be <= 999999.99")
    @Digits(integer = 6, fraction = 2, message = "Amount max 2 decimal places")
    BigDecimal amount,

    @NotNull(message = "Currency is required")
    @SupportedCurrency
    String currency,

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID max 50 characters")
    String customerId,

    @NotNull(message = "Payment method is required")
    @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|CRYPTO", message = "Invalid payment method")
    String paymentMethod,

    @Size(max = 2, message = "Country code max 2 characters")
    String country,

    Map<String, Object> metadata
) {}