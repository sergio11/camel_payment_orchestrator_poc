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
    private CustomerRiskPort customerRiskPort;

    @Inject
    private GeoRiskPort geoRiskPort;

    @Inject
    private VelocityPort velocityPort;

    @Override
    public PaymentMessage enrich(PaymentMessage message) {
        PaymentMetadataDTO enrichedMetadata = enrichWithRiskData(message);
        return message.withMetadata(enrichedMetadata);
    }

    private PaymentMetadataDTO enrichWithRiskData(PaymentMessage message) {
        PaymentMetadataDTO source = message.metadata() != null ? message.metadata() : PaymentMetadataDTO.empty();
        return source.conEnrichment(
            customerRiskPort.determineRiskTier(message.customerId()),
            velocityPort.calculateVelocityScore(message.customerId()),
            geoRiskPort.calculateGeoRiskScore(message.country())
        );
    }
}
