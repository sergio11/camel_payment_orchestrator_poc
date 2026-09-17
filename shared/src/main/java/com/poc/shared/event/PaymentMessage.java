package com.poc.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.poc.shared.dto.PaymentMetadataDTO;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    PaymentMetadataDTO metadata,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime timestamp
) implements Serializable {

    public PaymentMessage withMetadata(PaymentMetadataDTO metadata) {
        return new PaymentMessage(
            eventId, paymentId, amount, currency, customerId,
            paymentMethod, country, attemptCount, isNewPaymentMethod,
            customerAgeDays, timeZone, metadata, timestamp
        );
    }

    public void validate() {
        if (paymentId == null || amount == null || currency == null
            || customerId == null || paymentMethod == null) {
            throw new com.poc.shared.exception.InvalidPaymentException(
                paymentId != null ? paymentId : "unknown",
                "Invalid payment fields: required fields missing"
            );
        }
    }
}
