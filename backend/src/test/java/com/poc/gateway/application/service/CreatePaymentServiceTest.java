package com.poc.gateway.application.service;

import com.poc.gateway.application.mapper.PaymentMetadataApplicationMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentServiceTest {

    @Mock
    PaymentRepositoryPort paymentRepo;

    @Mock
    OutboxRepositoryPort outboxRepo;

    @Mock
    EventPublisherPort eventPublisher;

    @Mock
    PaymentEventSerializer serializer;

    @Mock
    PaymentMetadataApplicationMapper metadataMapper;

    @InjectMocks
    CreatePaymentService service;

    @Test
    @DisplayName("Should create payment and publish event on new payment")
    void execute_createsPaymentAndPublishesEvent() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );
        String key = "idem-key-123";

        Payment savedPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(metadataMapper.toDomain(any())).thenReturn(PaymentMetadata.empty());
        when(paymentRepo.save(any(Payment.class), eq(key))).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        Payment result = service.execute(request, key);

        assertNotNull(result);
        assertEquals("USD", result.currency());
        assertEquals("cust-1", result.customerId());
        assertEquals(PaymentStatus.PENDING, result.status());
        verify(paymentRepo).save(any(Payment.class), eq(key));
        verify(outboxRepo).persist(any());
        verify(eventPublisher).publishPaymentReceived(any(PaymentReceivedEvent.class));
    }

    @Test
    @DisplayName("Should return existing payment when idempotency key already exists")
    void execute_existingKey_returnsExistingPayment() {
        String key = "existing-key";
        Payment existing = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("50.00"), "EUR", "cust-2", "CARD", "DE", PaymentMetadataDTO.empty()
        );

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Payment result = service.execute(request, key);

        assertNotNull(result);
        assertEquals(existing.id(), result.id());
        verify(paymentRepo, never()).save(any(), anyString());
        verify(outboxRepo, never()).persist(any());
    }

    @Test
    @DisplayName("Should generate key when blank idempotency key is provided")
    void execute_blankIdempotencyKey_generatesKey() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );

        Payment savedPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(paymentRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(metadataMapper.toDomain(any())).thenReturn(PaymentMetadata.empty());
        when(paymentRepo.save(any(Payment.class), anyString())).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        Payment result = service.execute(request, "  ");

        assertNotNull(result);
        assertEquals("USD", result.currency());
        assertEquals("cust-1", result.customerId());
        verify(paymentRepo).save(any(Payment.class), anyString());
    }

    @Test
    @DisplayName("Should generate key when null idempotency key is provided")
    void execute_nullIdempotencyKey_generatesKey() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );

        Payment savedPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(paymentRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(metadataMapper.toDomain(any())).thenReturn(PaymentMetadata.empty());
        when(paymentRepo.save(any(Payment.class), anyString())).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        Payment result = service.execute(request, null);

        assertNotNull(result);
        assertEquals("USD", result.currency());
        assertEquals("cust-1", result.customerId());
        verify(paymentRepo).save(any(Payment.class), anyString());
    }

    @Test
    @DisplayName("Should log error but still return payment when Kafka publish fails")
    void execute_publishFails_logsError() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );
        String key = "idem-key-456";

        Payment savedPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(metadataMapper.toDomain(any())).thenReturn(PaymentMetadata.empty());
        when(paymentRepo.save(any(Payment.class), eq(key))).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(false);

        Payment result = service.execute(request, key);

        assertNotNull(result);
        verify(outboxRepo, never()).markSent(any());
    }
}
