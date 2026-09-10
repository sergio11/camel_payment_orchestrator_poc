package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.PaymentMetadataDTO;
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
    PaymentMapper mapper;

    @InjectMocks
    CreatePaymentService service;

    @Test
    void execute_createsPaymentAndPublishesEvent() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );
        String key = "idem-key-123";

        Payment domainPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        Payment savedPayment = domainPayment.withStatus(PaymentStatus.PENDING);

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(mapper.toDomain(any(PaymentRequestDTO.class))).thenReturn(domainPayment);
        when(paymentRepo.save(any(Payment.class), eq(key))).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(true);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(savedPayment.id().toString(), BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentResponseDTO result = service.execute(request, key);

        assertNotNull(result);
        verify(paymentRepo).save(any(Payment.class), eq(key));
        verify(outboxRepo).persist(any());
        verify(eventPublisher).publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any()
        );
    }

    @Test
    void execute_existingKey_returnsExistingPayment() {
        String key = "existing-key";
        Payment existing = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(existing.id().toString(), BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentResponseDTO result = service.execute(
            new PaymentRequestDTO(BigDecimal.TEN, "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()),
            key
        );

        assertNotNull(result);
        verify(paymentRepo, never()).save(any(), anyString());
        verify(outboxRepo, never()).persist(any());
    }

    @Test
    void execute_blankIdempotencyKey_generatesKey() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );

        Payment domainPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        Payment savedPayment = domainPayment.withStatus(PaymentStatus.PENDING);

        when(paymentRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(mapper.toDomain(any(PaymentRequestDTO.class))).thenReturn(domainPayment);
        when(paymentRepo.save(any(Payment.class), anyString())).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(true);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(savedPayment.id().toString(), BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentResponseDTO result = service.execute(request, "  ");

        assertNotNull(result);
        verify(paymentRepo).save(any(Payment.class), anyString());
    }

    @Test
    void execute_nullIdempotencyKey_generatesKey() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );

        Payment domainPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        Payment savedPayment = domainPayment.withStatus(PaymentStatus.PENDING);

        when(paymentRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(mapper.toDomain(any(PaymentRequestDTO.class))).thenReturn(domainPayment);
        when(paymentRepo.save(any(Payment.class), anyString())).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(true);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(savedPayment.id().toString(), BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentResponseDTO result = service.execute(request, null);

        assertNotNull(result);
        verify(paymentRepo).save(any(Payment.class), anyString());
    }

    @Test
    void execute_publishFails_logsError() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadataDTO.empty()
        );
        String key = "idem-key-456";

        Payment domainPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        Payment savedPayment = domainPayment.withStatus(PaymentStatus.PENDING);

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(mapper.toDomain(any(PaymentRequestDTO.class))).thenReturn(domainPayment);
        when(paymentRepo.save(any(Payment.class), eq(key))).thenReturn(savedPayment);
        when(serializer.serialize(any(Payment.class))).thenReturn("{}");
        when(eventPublisher.publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(false);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(savedPayment.id().toString(), BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentResponseDTO result = service.execute(request, key);

        assertNotNull(result);
        verify(outboxRepo, never()).markSent(any());
    }
}
