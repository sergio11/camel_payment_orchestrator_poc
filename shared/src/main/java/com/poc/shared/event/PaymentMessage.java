package com.poc.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record PaymentMessage(
    String eventId,
    String paymentId,
    BigDecimal amount,
    String currency,
    String customerId,
    String paymentMethod,
    String country,
    int attemptCount,
    boolean isNewPaymentMethod,
    int customerAgeDays,
    String timeZone,
    Map<String, Object> metadata,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime timestamp
) implements Serializable {}
