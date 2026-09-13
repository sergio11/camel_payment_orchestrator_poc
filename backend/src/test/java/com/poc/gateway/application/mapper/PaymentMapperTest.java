package com.poc.gateway.application.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.CreatePaymentCommand;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {

    private final PaymentMetadataApplicationMapper metadataMapper = new com.poc.gateway.application.mapper.PaymentMetadataApplicationMapperImpl();
    private final PaymentMapper mapper;

    PaymentMapperTest() {
        com.poc.gateway.application.mapper.PaymentMapperImpl impl = new com.poc.gateway.application.mapper.PaymentMapperImpl();
        impl.metadataMapper = metadataMapper;
        mapper = impl;
    }

    @Test
    void toDomain_fromRequest() {
        PaymentRequestDTO req = new PaymentRequestDTO(new BigDecimal("50"), "EUR", "cust", "CARD", "DE", PaymentMetadataDTO.empty());
        Payment p = mapper.toDomain(req);
        assertNull(p.id());
        assertEquals(new BigDecimal("50"), p.amount());
        assertEquals("EUR", p.currency());
        assertNull(p.status());
        assertNull(p.createdAt());
        assertNull(p.updatedAt());
    }

    @Test
    void toDomain_setsDefaultValues() {
        PaymentRequestDTO req = new PaymentRequestDTO(new BigDecimal("10"), "USD", "c1", "CARD", "US", PaymentMetadataDTO.empty());
        Payment p = mapper.toDomain(req);
        assertNull(p.id());
        assertNull(p.status());
        assertNull(p.provider());
        assertNull(p.failureReason());
        assertNull(p.createdAt());
        assertNull(p.updatedAt());
    }

    @Test
    void toResponseDTO_fromPayment() {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentStatus.APPROVED, null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now());
        PaymentResponseDTO dto = mapper.toResponseDTO(p);
        assertEquals(p.id().toString(), dto.id());
        assertEquals("APPROVED", dto.status());
        assertEquals("USD", dto.currency());
        assertEquals(new BigDecimal("100"), dto.amount());
    }

    @Test
    void toResponseDTO_nullStatus() {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US", null, null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now());
        PaymentResponseDTO dto = mapper.toResponseDTO(p);
        assertNull(dto.status());
    }

    @Test
    void toResponseDTO_nullId() {
        Payment p = new Payment(null, new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentStatus.PENDING, null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now());
        PaymentResponseDTO dto = mapper.toResponseDTO(p);
        assertNull(dto.id());
    }

    @Test
    void toMetadataDTO_null() {
        assertNull(metadataMapper.toDTO(null));
    }

    @Test
    void toMetadataDomain_null() {
        assertNull(metadataMapper.toDomain(null));
    }

    @Test
    void toDomain_nullRequest_returnsNull() {
        Payment result = mapper.toDomain(null);
        assertNull(result);
    }

    @Test
    void toResponseDTO_nullPayment_returnsNull() {
        PaymentResponseDTO result = mapper.toResponseDTO(null);
        assertNull(result);
    }

    @Test
    void toResponseDTO_withNonEmptyMetadata_mapsAllFields() {
        PaymentMetadata metadata = new PaymentMetadata("order-123", 3, true, 30, "low", "2026-01-01T00:00:00", 5, 10);
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentStatus.APPROVED, "stripe", null, metadata, LocalDateTime.now(), LocalDateTime.now());
        PaymentResponseDTO dto = mapper.toResponseDTO(p);
        assertNotNull(dto.metadata());
        assertEquals("order-123", dto.metadata().orderId());
        assertEquals(3, dto.metadata().attempts());
        assertEquals(true, dto.metadata().isNewPaymentMethod());
        assertEquals(30, dto.metadata().paymentMethodAgeDays());
        assertEquals("low", dto.metadata().customerRiskTier());
        assertEquals("2026-01-01T00:00:00", dto.metadata().enrichedAt());
        assertEquals(5, dto.metadata().velocityScore());
        assertEquals(10, dto.metadata().geoRiskScore());
    }

    @Test
    void toCommand_nullRequest_returnsNull() {
        CreatePaymentCommand result = mapper.toCommand(null);
        assertNull(result);
    }

    @Test
    void toCommand_fromRequest() {
        PaymentRequestDTO req = new PaymentRequestDTO(new BigDecimal("50"), "EUR", "cust", "CARD", "DE", PaymentMetadataDTO.empty());
        CreatePaymentCommand cmd = mapper.toCommand(req);
        assertNotNull(cmd);
        assertEquals(new BigDecimal("50"), cmd.amount());
        assertEquals("EUR", cmd.currency());
        assertEquals("cust", cmd.customerId());
        assertEquals("CARD", cmd.paymentMethod());
        assertEquals("DE", cmd.country());
    }

    @Test
    void toDomain_withNullMetadataDTO() {
        PaymentRequestDTO req = new PaymentRequestDTO(new BigDecimal("50"), "EUR", "cust", "CARD", "DE", null);
        Payment p = mapper.toDomain(req);
        assertNotNull(p);
        assertNull(p.metadata());
    }

    @Test
    void toMetadataDomain_withNonNull() {
        PaymentMetadataDTO dto = new PaymentMetadataDTO("order-1", 5, true, 10, "high", "2026-01-01T00:00:00", 20, 50);
        PaymentMetadata result = metadataMapper.toDomain(dto);
        assertNotNull(result);
        assertEquals("order-1", result.orderId());
        assertEquals(5, result.attempts());
        assertEquals(true, result.isNewPaymentMethod());
        assertEquals(10, result.paymentMethodAgeDays());
        assertEquals("high", result.customerRiskTier());
        assertEquals(20, result.velocityScore());
        assertEquals(50, result.geoRiskScore());
    }
}
