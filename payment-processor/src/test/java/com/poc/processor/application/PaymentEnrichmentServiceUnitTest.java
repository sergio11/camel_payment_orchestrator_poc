package com.poc.processor.application;

import com.poc.processor.port.outbound.CustomerRiskPort;
import com.poc.processor.port.outbound.GeoRiskPort;
import com.poc.processor.port.outbound.VelocityPort;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEnrichmentServiceUnitTest {

    @Mock
    CustomerRiskPort customerRiskPort;

    @Mock
    GeoRiskPort geoRiskPort;

    @Mock
    VelocityPort velocityPort;

    @InjectMocks
    PaymentEnrichmentService service;

    @Test
    @DisplayName("enrich delegates to all three ports")
    void enrich_delegatesToPorts() {
        when(customerRiskPort.determineRiskTier(anyString())).thenReturn("LOW");
        when(geoRiskPort.calculateGeoRiskScore(anyString())).thenReturn(10);
        when(velocityPort.calculateVelocityScore(anyString())).thenReturn(5);

        PaymentMessage msg = createMessage();
        PaymentMessage enriched = service.enrich(msg);

        assertNotNull(enriched);
        verify(customerRiskPort).determineRiskTier("cust-1");
        verify(geoRiskPort).calculateGeoRiskScore("US");
        verify(velocityPort).calculateVelocityScore("cust-1");
    }

    @Test
    @DisplayName("enrich preserves original fields")
    void enrich_preservesOriginalFields() {
        when(customerRiskPort.determineRiskTier(anyString())).thenReturn("LOW");
        when(geoRiskPort.calculateGeoRiskScore(anyString())).thenReturn(10);
        when(velocityPort.calculateVelocityScore(anyString())).thenReturn(5);

        PaymentMessage msg = createMessage();
        PaymentMessage enriched = service.enrich(msg);

        assertEquals("evt-1", enriched.eventId());
        assertEquals("pay-1", enriched.paymentId());
        assertEquals(new BigDecimal("100.00"), enriched.amount());
        assertEquals("USD", enriched.currency());
        assertEquals("cust-1", enriched.customerId());
        assertEquals("CREDIT_CARD", enriched.paymentMethod());
        assertEquals("US", enriched.country());
    }

    @Test
    @DisplayName("enrich with null metadata creates empty metadata")
    void enrich_nullMetadata() {
        when(customerRiskPort.determineRiskTier(anyString())).thenReturn("LOW");
        when(geoRiskPort.calculateGeoRiskScore(anyString())).thenReturn(10);
        when(velocityPort.calculateVelocityScore(anyString())).thenReturn(5);

        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100"), "USD", "cust-1",
            "CARD", "US", 0, false, 0, "UTC",
            null, LocalDateTime.now()
        );

        PaymentMessage enriched = service.enrich(msg);

        assertNotNull(enriched.metadata());
    }

    @Test
    @DisplayName("enrichedAt timestamp is set")
    void enrich_setsEnrichedAt() {
        when(customerRiskPort.determineRiskTier(anyString())).thenReturn("LOW");
        when(geoRiskPort.calculateGeoRiskScore(anyString())).thenReturn(10);
        when(velocityPort.calculateVelocityScore(anyString())).thenReturn(5);

        PaymentMessage enriched = service.enrich(createMessage());

        assertNotNull(enriched.metadata().enrichedAt());
    }

    private PaymentMessage createMessage() {
        return new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }
}
