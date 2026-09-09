package com.poc.gateway.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {

    private final PaymentMapper mapper = PaymentMapper.INSTANCE;

    @Test
    void toDomain_mapsAllFieldsFromRequest() {
        PaymentMetadataDTO metadata = new PaymentMetadataDTO(
            "order-1",
            3,
            true,
            120,
            "LOW",
            "2026-01-01T00:00:00",
            45,
            22
        );
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("150.00"),
            "USD",
            "cust-42",
            "CREDIT_CARD",
            "US",
            metadata
        );

        Payment entity = mapper.toDomain(request);

        assertNotNull(entity);
        assertNotNull(entity.id());
        assertInstanceOf(UUID.class, entity.id());
        assertEquals(PaymentStatus.PENDING, entity.status());
        assertEquals(request.amount(), entity.amount());
        assertEquals(request.currency(), entity.currency());
        assertEquals(request.customerId(), entity.customerId());
        assertEquals(request.paymentMethod(), entity.paymentMethod());
        assertEquals(request.country(), entity.country());
        assertEquals(metadata.orderId(), entity.metadata().orderId());
        assertEquals(metadata.attempts(), entity.metadata().attempts());
        assertEquals(metadata.isNewPaymentMethod(), entity.metadata().isNewPaymentMethod());
        assertEquals(metadata.paymentMethodAgeDays(), entity.metadata().paymentMethodAgeDays());
        assertEquals(metadata.customerRiskTier(), entity.metadata().customerRiskTier());
        assertEquals(metadata.enrichedAt(), entity.metadata().enrichedAt());
        assertEquals(metadata.velocityScore(), entity.metadata().velocityScore());
        assertEquals(metadata.geoRiskScore(), entity.metadata().geoRiskScore());
        assertNull(entity.provider());
        assertNull(entity.failureReason());
        assertNotNull(entity.createdAt());
        assertNotNull(entity.updatedAt());
    }

    @Test
    void toDomain_emptyMetadata_mapsCorrectly() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("10.00"),
            "EUR",
            "cust-1",
            "WALLET",
            "DE",
            PaymentMetadataDTO.empty()
        );

        Payment entity = mapper.toDomain(request);

        assertNotNull(entity);
        assertNotNull(entity.metadata());
        assertEquals(new BigDecimal("10.00"), entity.amount());
    }

    @Test
    void toDomain_nullMetadata_mapsCorrectly() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("10.00"),
            "EUR",
            "cust-1",
            "WALLET",
            "DE",
            null
        );

        Payment entity = mapper.toDomain(request);

        assertNull(entity.metadata());
        assertEquals(new BigDecimal("10.00"), entity.amount());
    }

    @Test
    void toResponseDTO_mapsAllFieldsFromEntity() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        PaymentMetadata metadata = new PaymentMetadata(
            "order-99",
            5,
            false,
            30,
            "MEDIUM",
            "2026-06-15T12:00:00",
            70,
            55
        );
        Payment entity = new Payment(
            id,
            new BigDecimal("250.50"),
            "GBP",
            "cust-99",
            "DEBIT_CARD",
            "UK",
            PaymentStatus.APPROVED,
            "provider-a",
            null,
            metadata,
            now,
            now
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertEquals(id.toString(), response.id());
        assertEquals(new BigDecimal("250.50"), response.amount());
        assertEquals("GBP", response.currency());
        assertEquals("cust-99", response.customerId());
        assertEquals("DEBIT_CARD", response.paymentMethod());
        assertEquals("UK", response.country());
        assertEquals("APPROVED", response.status());
        assertEquals("provider-a", response.provider());
        assertNull(response.failureReason());
        assertNotNull(response.metadata());
        assertEquals("order-99", response.metadata().orderId());
        assertEquals(5, response.metadata().attempts());
        assertEquals(false, response.metadata().isNewPaymentMethod());
        assertEquals(30, response.metadata().paymentMethodAgeDays());
        assertEquals("MEDIUM", response.metadata().customerRiskTier());
        assertEquals("2026-06-15T12:00:00", response.metadata().enrichedAt());
        assertEquals(70, response.metadata().velocityScore());
        assertEquals(55, response.metadata().geoRiskScore());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void toResponseDTO_nullProviderAndFailureReason_mapsToNull() {
        Payment entity = new Payment(
            UUID.randomUUID(),
            new BigDecimal("1.00"),
            "USD",
            "cust-1",
            "CREDIT_CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            PaymentMetadata.empty(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertNull(response.provider());
        assertNull(response.failureReason());
        assertNotNull(response.metadata());
    }

    @Test
    void toResponseDTO_failedStatus_mapsCorrectly() {
        Payment entity = new Payment(
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            "cust-1",
            "CRYPTO",
            "US",
            PaymentStatus.FAILED,
            "provider-b",
            "Timeout after 30s",
            PaymentMetadata.empty(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertEquals("FAILED", response.status());
        assertEquals("provider-b", response.provider());
        assertEquals("Timeout after 30s", response.failureReason());
    }

    @Test
    void toResponseDTO_nullStatus_mapsToNull() {
        Payment entity = new Payment(
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            "cust-1",
            "CREDIT_CARD",
            "US",
            null,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertNull(response.status());
    }

    @Test
    void toResponseDTO_allNullOptionalFields_mapsCorrectly() {
        Payment entity = new Payment(
            UUID.randomUUID(),
            new BigDecimal("1.00"),
            "USD",
            "cust-1",
            "CARD",
            "US",
            null,
            null,
            null,
            null,
            null,
            null
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertNull(response.status());
        assertNull(response.provider());
        assertNull(response.failureReason());
        assertNull(response.metadata());
        assertNull(response.createdAt());
        assertNull(response.updatedAt());
    }

    @Test
    void toDomain_nullRequest_returnsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    void toResponseDTO_nullPayment_returnsNull() {
        assertNull(mapper.toResponseDTO(null));
    }

    @Test
    void toResponseDTO_nullId_mapsToNullId() {
        Payment entity = new Payment(
            null,
            new BigDecimal("1.00"),
            "USD",
            "cust-1",
            "CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertNull(response.id());
    }
}
