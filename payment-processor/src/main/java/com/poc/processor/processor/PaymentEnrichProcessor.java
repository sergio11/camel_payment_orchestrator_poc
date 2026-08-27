package com.poc.processor.processor;

import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@Named("paymentEnrichProcessor")
@ApplicationScoped
public class PaymentEnrichProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);
        
        // Enrich with additional risk data (simulated)
        // In production, this would call external risk services
        var enrichedMetadata = enrichWithRiskData(message);
        
        // Create enriched message
        PaymentMessage enriched = new PaymentMessage(
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
        
        exchange.getIn().setBody(enriched);
    }

    private java.util.Map<String, Object> enrichWithRiskData(PaymentMessage message) {
        var metadata = new java.util.HashMap<>(message.metadata() != null ? message.metadata() : new java.util.HashMap<>());
        
        // Simulate risk data enrichment
        metadata.put("enrichedAt", java.time.LocalDateTime.now().toString());
        metadata.put("customerRiskTier", determineCustomerRiskTier(message.customerId()));
        metadata.put("velocityScore", calculateVelocityScore(message.customerId()));
        metadata.put("geoRiskScore", calculateGeoRiskScore(message.country()));
        
        return metadata;
    }

    private String determineCustomerRiskTier(String customerId) {
        // Simulated: hash customerId to get consistent tier
        int hash = Math.floorMod(customerId.hashCode(), 3);
        return switch (hash) {
            case 0 -> "LOW";
            case 1 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    private int calculateVelocityScore(String customerId) {
        // Simulated: consistent pseudo-random based on customerId
        return Math.floorMod(customerId.hashCode(), 100);
    }

    private int calculateGeoRiskScore(String country) {
        if (country == null) return 0;
        // High risk countries
        return switch (country) {
            case "XX", "YY" -> 80;
            case "ZZ", "WW" -> 50;
            default -> 10;
        };
    }
}