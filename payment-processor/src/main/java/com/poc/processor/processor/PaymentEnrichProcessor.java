package com.poc.processor.processor;

import com.poc.shared.dto.PaymentMetadataDTO;
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
        
        PaymentMetadataDTO enrichedMetadata = enrichWithRiskData(message);
        
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
