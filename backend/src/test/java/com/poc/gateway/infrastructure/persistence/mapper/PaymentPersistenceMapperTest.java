package com.poc.gateway.infrastructure.persistence.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.infrastructure.persistence.entity.PaymentEntity;
import com.poc.gateway.infrastructure.persistence.entity.PaymentMetadataEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentPersistenceMapperTest {

    @Mock PaymentMetadataMapper metadataMapper;
    @InjectMocks PaymentPersistenceMapperImpl mapper;

    @Test
    void toEntity_nullPayment_returnsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void toDomain_nullEntity_returnsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    void toEntity_mapsAllFields_pending() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, "Stripe", "err", null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        assertEquals(domain.id(), entity.id);
        assertEquals(0, entity.amount.compareTo(new BigDecimal("100")));
        assertEquals("USD", entity.currency);
        assertEquals("c1", entity.customerId);
        assertEquals("CARD", entity.paymentMethod);
        assertEquals("US", entity.country);
        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.PENDING, entity.status);
        assertEquals("Stripe", entity.provider);
        assertEquals("err", entity.failureReason);
        assertNotNull(entity.createdAt);
        assertNotNull(entity.updatedAt);
    }

    @Test
    void toEntity_mapsProcessingStatus() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("50"), "EUR", "c2", "WALLET", "DE",
            PaymentStatus.PROCESSING, "PayPal", null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.PROCESSING, entity.status);
    }

    @Test
    void toEntity_mapsApprovedStatus() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("75"), "GBP", "c3", "CARD", "UK",
            PaymentStatus.APPROVED, "Stripe", null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.APPROVED, entity.status);
    }

    @Test
    void toEntity_mapsRejectedStatus() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("200"), "JPY", "c4", "BANK_TRANSFER", "JP",
            PaymentStatus.REJECTED, null, "declined", null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.REJECTED, entity.status);
    }

    @Test
    void toEntity_mapsFailedStatus() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("300"), "CAD", "c5", "CARD", "CA",
            PaymentStatus.FAILED, "Stripe", "timeout", null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.FAILED, entity.status);
    }

    @Test
    void toEntity_mapsReviewStatus() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("400"), "AUD", "c6", "CARD", "AU",
            PaymentStatus.REVIEW, "WorldPay", null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.REVIEW, entity.status);
    }

    @Test
    void toEntity_nullId_generatesUuid() {
        Payment domain = new Payment(
            null, new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, null, null, null,
            null, null
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertNotNull(entity.id);
        assertNotNull(entity.createdAt);
        assertNotNull(entity.updatedAt);
    }

    @Test
    void toEntity_nullTimestamps_generatesNow() {
        UUID id = UUID.randomUUID();
        Payment domain = new Payment(
            id, new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, null, null, null,
            null, null
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertEquals(id, entity.id);
        assertNotNull(entity.createdAt);
        assertNotNull(entity.updatedAt);
    }

    @Test
    void toEntity_nullMetadata_handlesGracefully() {
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, null, null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        assertNull(entity.metadata);
        verify(metadataMapper).toEntity(null);
    }

    @Test
    void toEntity_withMetadata_mapsMetadata() {
        PaymentMetadata meta = new PaymentMetadata("order-1", 3, true, 30, "LOW", null, null, null);
        Payment domain = new Payment(
            UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, "Stripe", "err", meta,
            LocalDateTime.now(), LocalDateTime.now()
        );

        PaymentMetadataEntity metaEntity = new PaymentMetadataEntity();
        when(metadataMapper.toEntity(meta)).thenReturn(metaEntity);

        PaymentEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        verify(metadataMapper).toEntity(meta);
    }

    @Test
    void toDomain_mapsAllFields_approved() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("50");
        entity.currency = "EUR";
        entity.customerId = "c2";
        entity.paymentMethod = "WALLET";
        entity.country = "DE";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.APPROVED;
        entity.provider = "Stripe";
        entity.failureReason = null;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(entity.id, domain.id());
        assertEquals(0, domain.amount().compareTo(new BigDecimal("50")));
        assertEquals("EUR", domain.currency());
        assertEquals("c2", domain.customerId());
        assertEquals("WALLET", domain.paymentMethod());
        assertEquals("DE", domain.country());
        assertEquals(PaymentStatus.APPROVED, domain.status());
        assertEquals("Stripe", domain.provider());
    }

    @Test
    void toDomain_mapsPendingStatus() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("10");
        entity.currency = "USD";
        entity.customerId = "c1";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertEquals(PaymentStatus.PENDING, domain.status());
    }

    @Test
    void toDomain_mapsProcessingStatus() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("10");
        entity.currency = "USD";
        entity.customerId = "c1";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.PROCESSING;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertEquals(PaymentStatus.PROCESSING, domain.status());
    }

    @Test
    void toDomain_mapsRejectedStatus() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("10");
        entity.currency = "USD";
        entity.customerId = "c1";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.REJECTED;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertEquals(PaymentStatus.REJECTED, domain.status());
    }

    @Test
    void toDomain_mapsFailedStatus() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("10");
        entity.currency = "USD";
        entity.customerId = "c1";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.FAILED;
        entity.failureReason = "timeout";
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertEquals(PaymentStatus.FAILED, domain.status());
        assertEquals("timeout", domain.failureReason());
    }

    @Test
    void toDomain_mapsReviewStatus() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("10");
        entity.currency = "USD";
        entity.customerId = "c1";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.REVIEW;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertEquals(PaymentStatus.REVIEW, domain.status());
    }

    @Test
    void toDomain_nullMetadata_handlesGracefully() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("50");
        entity.currency = "EUR";
        entity.customerId = "c2";
        entity.paymentMethod = "WALLET";
        entity.country = "DE";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.APPROVED;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertNull(domain.metadata());
    }

    @Test
    void toDomain_nullAmount_handlesGracefully() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = null;
        entity.currency = "USD";
        entity.customerId = "c1";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        when(metadataMapper.toDomain(null)).thenReturn(null);

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertNull(domain.amount());
    }

    @Test
    void toDomain_withMetadata_mapsMetadata() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("50");
        entity.currency = "EUR";
        entity.customerId = "c2";
        entity.status = com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.APPROVED;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();

        PaymentMetadataEntity metaEntity = new PaymentMetadataEntity();
        PaymentMetadata metaDomain = new PaymentMetadata("o-1", 1, false, 10, "HIGH", null, null, null);
        when(metadataMapper.toDomain(metaEntity)).thenReturn(metaDomain);
        entity.metadata = metaEntity;

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(metaDomain, domain.metadata());
    }

    @Test
    void roundTrip_pending_toEntityAndToDomain() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Payment domain = new Payment(
            id, new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, "Stripe", null, null,
            now, now
        );

        PaymentEntity entity = mapper.toEntity(domain);
        assertEquals(com.poc.gateway.infrastructure.persistence.entity.PaymentStatus.PENDING, entity.status);

        when(metadataMapper.toDomain(null)).thenReturn(null);
        Payment backToDomain = mapper.toDomain(entity);
        assertEquals(PaymentStatus.PENDING, backToDomain.status());
    }

    @Test
    void roundTrip_allStatuses_toEntityAndToDomain() {
        for (PaymentStatus status : PaymentStatus.values()) {
            UUID id = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();
            Payment domain = new Payment(
                id, new BigDecimal("100"), "USD", "c1", "CARD", "US",
                status, "Stripe", null, null,
                now, now
            );

            PaymentEntity entity = mapper.toEntity(domain);
            assertNotNull(entity.status, "Entity status should not be null for " + status);

            when(metadataMapper.toDomain(null)).thenReturn(null);
            Payment backToDomain = mapper.toDomain(entity);
            assertEquals(status, backToDomain.status(), "Round-trip failed for " + status);
        }
    }
}
