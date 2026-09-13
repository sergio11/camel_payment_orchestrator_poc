package com.poc.gateway.domain.model;

import com.poc.gateway.domain.PaymentMetadata;
import java.math.BigDecimal;

public record CreatePaymentCommand(
    BigDecimal amount,
    String currency,
    String customerId,
    String paymentMethod,
    String country,
    PaymentMetadata metadata
) {}
