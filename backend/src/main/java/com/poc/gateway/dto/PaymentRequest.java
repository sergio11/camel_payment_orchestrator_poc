package com.poc.gateway.dto;

import com.poc.gateway.validator.SupportedCurrency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public class PaymentRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be >= 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must be <= 999999.99")
    @Digits(integer = 6, fraction = 2, message = "Amount max 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @SupportedCurrency
    private String currency;

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID max 50 characters")
    private String customerId;

    @NotNull(message = "Payment method is required")
    @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|CRYPTO", message = "Invalid payment method")
    private String paymentMethod;

    @Size(max = 2, message = "Country code max 2 characters")
    private String country;

    private Map<String, Object> metadata;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}