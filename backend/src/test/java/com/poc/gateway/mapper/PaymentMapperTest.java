package com.poc.gateway.mapper;

import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.shared.dto.PaymentRequest;
import com.poc.shared.dto.PaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {

    @Test
    void toEntity_mapsAllFieldsFromRequest() {
        PaymentRequest request = new PaymentRequest(
            new BigDecimal("150.00"),
            "USD",
            "cust-42",
            "CREDIT_CARD",
            "US",
            Map.of("key", "value")
        );

        Payment entity = PaymentMapper.toEntity(request);

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
    void toEntity_nullMetadata_mapsCorrectly() {
        PaymentRequest request = new PaymentRequest(
            new BigDecimal("10.00"),
            "EUR",
            "cust-1",
            "WALLET",
            "DE",
            null
        );

        Payment entity = PaymentMapper.toEntity(request);

        assertNull(entity.metadata());
        assertEquals(new BigDecimal("10.00"), entity.amount());
    }

    @Test
    void toResponse_mapsAllFieldsFromEntity() {
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

        PaymentResponse response = PaymentMapper.toResponse(entity);

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
    void toResponse_nullProviderAndFailureReason_mapsToNull() {
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

        PaymentResponse response = PaymentMapper.toResponse(entity);

        assertNull(response.provider());
        assertNull(response.failureReason());
        assertNull(response.metadata());
    }

    @Test
    void toResponse_failedStatus_mapsCorrectly() {
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

        PaymentResponse response = PaymentMapper.toResponse(entity);

        assertEquals("FAILED", response.status());
        assertEquals("provider-b", response.provider());
        assertEquals("Timeout after 30s", response.failureReason());
    }

    @Test
    void toResponse_nullStatus_mapsToNull() {
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

        PaymentResponse response = PaymentMapper.toResponse(entity);

        assertNull(response.status());
    }
}
