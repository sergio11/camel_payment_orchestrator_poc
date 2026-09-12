package com.poc.processor.application;

import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.outbound.CustomerRiskPort;
import com.poc.processor.port.outbound.GeoRiskPort;
import com.poc.processor.port.outbound.VelocityPort;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PaymentEnrichmentService implements EnrichPaymentUseCase {

    @Inject
    CustomerRiskPort customerRiskPort;

    @Inject
    GeoRiskPort geoRiskPort;

    @Inject
    VelocityPort velocityPort;

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
            customerRiskPort.determineRiskTier(message.customerId()),
            java.time.LocalDateTime.now().toString(),
            velocityPort.calculateVelocityScore(message.customerId()),
            geoRiskPort.calculateGeoRiskScore(message.country())
        );
    }
}
