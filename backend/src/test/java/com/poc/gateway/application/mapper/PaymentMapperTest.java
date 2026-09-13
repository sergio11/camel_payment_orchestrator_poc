package com.poc.gateway.application.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
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
}
