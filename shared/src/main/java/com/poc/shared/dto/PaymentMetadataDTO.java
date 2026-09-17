package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PaymentMetadataDTO(
    @JsonProperty("order_id")
    String orderId,

    @Min(value = 0, message = "Attempts must be >= 0")
    @Max(value = 100, message = "Attempts must be <= 100")
    @JsonProperty("attempts")
    Integer attempts,

    @JsonProperty("is_new_payment_method")
    Boolean isNewPaymentMethod,

    @Min(value = 0, message = "Payment method age days must be >= 0")
    @JsonProperty("payment_method_age_days")
    Integer paymentMethodAgeDays,

    @JsonProperty("customer_risk_tier")
    String customerRiskTier,

    @JsonProperty("enriched_at")
    String enrichedAt,

    @Min(value = 0, message = "Velocity score must be >= 0")
    @Max(value = 99, message = "Velocity score must be <= 99")
    @JsonProperty("velocity_score")
    Integer velocityScore,

    @Min(value = 0, message = "Geo risk score must be >= 0")
    @Max(value = 100, message = "Geo risk score must be <= 100")
    @JsonProperty("geo_risk_score")
    Integer geoRiskScore
) {
    public static PaymentMetadataDTO empty() {
        return new PaymentMetadataDTO(null, null, null, null, null, null, null, null);
    }

    public PaymentMetadataDTO conEnrichment(String customerRiskTier, Integer velocityScore, Integer geoRiskScore) {
        return new PaymentMetadataDTO(
            orderId, attempts, isNewPaymentMethod, paymentMethodAgeDays,
            customerRiskTier, java.time.LocalDateTime.now().toString(),
            velocityScore, geoRiskScore
        );
    }
}
