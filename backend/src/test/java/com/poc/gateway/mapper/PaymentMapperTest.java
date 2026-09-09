package com.poc.gateway.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {

    private final PaymentMapper mapper = PaymentMapper.INSTANCE;

    @Test
    void toDomain_mapsAllFieldsFromRequest() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("150.00"),
            "USD",
            "cust-42",
            "CREDIT_CARD",
            "US",
            Map.of("key", "value")
        );

        Payment entity = mapper.toDomain(request);

        assertEquals(request.amount(), entity.amount());
        assertEquals(request.currency(), entity.currency());
        assertEquals(request.customerId(), entity.customerId());
        assertEquals(request.paymentMethod(), entity.paymentMethod());
        assertEquals(request.country(), entity.country());
        assertEquals(request.metadata(), entity.metadata());
        assertNotNull(entity.id());
        assertInstanceOf(UUID.class, entity.id());
        assertEquals(PaymentStatus.PENDING, entity.status());
        assertNull(entity.provider());
        assertNull(entity.failureReason());
        assertNotNull(entity.createdAt());
        assertNotNull(entity.updatedAt());
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
            Map.of("foo", "bar"),
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
        assertEquals(Map.of("foo", "bar"), response.metadata());
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
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentResponseDTO response = mapper.toResponseDTO(entity);

        assertNull(response.provider());
        assertNull(response.failureReason());
        assertNull(response.metadata());
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
            Map.of(),
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
