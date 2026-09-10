package com.poc.processor.application;

import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PaymentEnrichmentService implements EnrichPaymentUseCase {

    @Override
    public PaymentMessage enrich(PaymentMessage message) {
        PaymentMetadataDTO enrichedMetadata = enrichWithRiskData(message);

        return new PaymentMessage(
            message.eventId(),
            message.paymentId(),
            message.amount(),
            message.currency(),
            message.customerId(),
            message.paymentMethod(),
            message.country(),
            message.attemptCount(),
            message.isNewPaymentMethod(),
            message.customerAgeDays(),
            message.timeZone(),
            enrichedMetadata,
            message.timestamp()
        );
    }

    private PaymentMetadataDTO enrichWithRiskData(PaymentMessage message) {
        PaymentMetadataDTO source = message.metadata() != null ? message.metadata() : PaymentMetadataDTO.empty();

        return new PaymentMetadataDTO(
            source.orderId(),
            source.attempts(),
            source.isNewPaymentMethod(),
            source.paymentMethodAgeDays(),
            determineCustomerRiskTier(message.customerId()),
            java.time.LocalDateTime.now().toString(),
            calculateVelocityScore(message.customerId()),
            calculateGeoRiskScore(message.country())
        );
    }

    private String determineCustomerRiskTier(String customerId) {
        int hash = Math.floorMod(customerId.hashCode(), 3);
        return switch (hash) {
            case 0 -> "LOW";
            case 1 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    private int calculateVelocityScore(String customerId) {
        return Math.floorMod(customerId.hashCode(), 100);
    }

    private int calculateGeoRiskScore(String country) {
        if (country == null) return 0;
        return switch (country) {
            case "XX", "YY" -> 80;
            case "ZZ", "WW" -> 50;
            default -> 10;
        };
    }
}
