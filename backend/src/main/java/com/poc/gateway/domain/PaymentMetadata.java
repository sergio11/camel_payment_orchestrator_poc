package com.poc.gateway.domain;

public record PaymentMetadata(
    String orderId,
    Integer attempts,
    Boolean isNewPaymentMethod,
    Integer paymentMethodAgeDays,
    String customerRiskTier,
    String enrichedAt,
    Integer velocityScore,
    Integer geoRiskScore
) {
    public static PaymentMetadata empty() {
        return new PaymentMetadata(null, null, null, null, null, null, null, null);
    }
}
