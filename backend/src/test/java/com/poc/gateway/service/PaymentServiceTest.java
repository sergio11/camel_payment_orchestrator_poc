package com.poc.gateway.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.exception.PaymentNotFoundException;
import com.poc.gateway.repository.OutboxEventRepository;
import com.poc.gateway.repository.PaymentRepository;
import com.poc.gateway.service.KafkaEventPublisher;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository repository;

    @Mock
    OutboxEventRepository outbox;

    @Mock
    KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    PaymentService service;

    private Payment createEntity(String id, String customerId, PaymentStatus status) {
        return new Payment(
            UUID.fromString(id),
            new BigDecimal("100.00"),
            "USD",
            customerId,
            "CREDIT_CARD",
            "US",
            status,
            null,
            null,
            PaymentMetadata.empty(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    @Test
    void createPayment_savesAndReturnsResponse() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("200.00"),
            "EUR",
            "cust-1",
            "CREDIT_CARD",
            "DE",
            PaymentMetadataDTO.empty()
        );

        when(outbox.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class), anyString())).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            return p.withStatus(PaymentStatus.PENDING);
        });
        when(kafkaEventPublisher.publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any(PaymentMetadataDTO.class)
        )).thenReturn(true);

        PaymentResponseDTO response = service.createPayment(request);

        assertNotNull(response.id());
        assertEquals(new BigDecimal("200.00"), response.amount());
        assertEquals("EUR", response.currency());
        assertEquals("cust-1", response.customerId());
        assertEquals("PENDING", response.status());
        verify(repository, times(1)).save(any(Payment.class), anyString());
        verify(kafkaEventPublisher, times(1)).publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any(PaymentMetadataDTO.class)
        );
    }

    @Test
    void createPayment_sameIdempotencyKey_returnsSamePaymentOnce() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("50.00"),
            "USD",
            "cust-idem",
            "CREDIT_CARD",
            "US",
            PaymentMetadataDTO.empty()
        );
        String key = UUID.randomUUID().toString();

        when(outbox.findByIdempotencyKey(eq(key))).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(eq(key))).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class), eq(key))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            return p.withStatus(PaymentStatus.PENDING);
        });
        when(kafkaEventPublisher.publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any(PaymentMetadataDTO.class)
        )).thenReturn(true);

        PaymentResponseDTO first = service.createPayment(request, key);
        PaymentResponseDTO second = service.createPayment(request, key);

        assertEquals(first.id(), second.id());
        verify(repository, times(1)).save(any(Payment.class), eq(key));
        verify(kafkaEventPublisher, times(1)).publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any(PaymentMetadataDTO.class)
        );
    }

    @Test
    void createPayment_existingKey_returnsStoredPaymentWithoutPublish() {
        String key = UUID.randomUUID().toString();
        Payment stored = createEntity(UUID.randomUUID().toString(), "cust-replay", PaymentStatus.PENDING);
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("50.00"),
            "USD",
            "cust-replay",
            "CREDIT_CARD",
            "US",
            PaymentMetadataDTO.empty()
        );

        when(outbox.findByIdempotencyKey(eq(key))).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(eq(key))).thenReturn(Optional.of(stored));

        PaymentResponseDTO response = service.createPayment(request, key);

        assertEquals(stored.id().toString(), response.id());
        verify(repository, never()).save(any(), anyString());
        verify(kafkaEventPublisher, never()).publishPaymentReceived(
            anyString(), any(), anyString(), anyString(), anyString(), anyString(), any(PaymentMetadataDTO.class)
        );
    }

    @Test
    void getPayment_returnsPaymentForValidId() {
        String id = UUID.randomUUID().toString();
        Payment entity = createEntity(id, "cust-1", PaymentStatus.APPROVED);
        when(repository.findById(UUID.fromString(id))).thenReturn(Optional.of(entity));

        PaymentResponseDTO response = service.getPayment(id);

        assertEquals(id, response.id());
        assertEquals("APPROVED", response.status());
        assertEquals("cust-1", response.customerId());
    }

    @Test
    void getPayment_throwsNotFoundExceptionForInvalidId() {
        String id = UUID.randomUUID().toString();
        when(repository.findById(UUID.fromString(id))).thenReturn(Optional.empty());

        PaymentNotFoundException ex = assertThrows(
            PaymentNotFoundException.class,
            () -> service.getPayment(id)
        );
        assertEquals(id, ex.getPaymentId());
    }

    @Test
    void listPayments_returnsAllPayments() {
        List<Payment> entities = List.of(
            createEntity(UUID.randomUUID().toString(), "cust-1", PaymentStatus.PENDING),
            createEntity(UUID.randomUUID().toString(), "cust-1", PaymentStatus.APPROVED)
        );
        when(repository.findAll(null, null, 20, 0)).thenReturn(entities);

        List<PaymentResponseDTO> results = service.listPayments(null, null, 20, 0);

        assertEquals(2, results.size());
        verify(repository).findAll(null, null, 20, 0);
    }

    @Test
    void listPayments_filtersByCustomerId() {
        List<Payment> entities = List.of(
            createEntity(UUID.randomUUID().toString(), "cust-1", PaymentStatus.PENDING)
        );
        when(repository.findAll("cust-1", null, 10, 0)).thenReturn(entities);

        List<PaymentResponseDTO> results = service.listPayments("cust-1", null, 10, 0);

        assertEquals(1, results.size());
        assertEquals("cust-1", results.get(0).customerId());
    }

    @Test
    void listPayments_filtersByStatus() {
        List<Payment> entities = List.of(
            createEntity(UUID.randomUUID().toString(), "cust-2", PaymentStatus.FAILED)
        );
        when(repository.findAll(null, PaymentStatus.FAILED, 20, 0)).thenReturn(entities);

        List<PaymentResponseDTO> results = service.listPayments(null, "FAILED", 20, 0);

        assertEquals(1, results.size());
        assertEquals("FAILED", results.get(0).status());
    }

    @Test
    void listPayments_convertsStatusStringToEnum() {
        when(repository.findAll(eq("cust-1"), eq(PaymentStatus.APPROVED), anyInt(), anyInt()))
            .thenReturn(List.of());

        service.listPayments("cust-1", "APPROVED", 20, 0);

        verify(repository).findAll("cust-1", PaymentStatus.APPROVED, 20, 0);
    }

    @Test
    void listPayments_nullStatus_passesNullToRepository() {
        when(repository.findAll("cust-1", null, 20, 0)).thenReturn(List.of());

        service.listPayments("cust-1", null, 20, 0);

        verify(repository).findAll("cust-1", null, 20, 0);
    }

    @Test
    void listPayments_emptyResult_returnsEmptyList() {
        when(repository.findAll("nonexistent", null, 20, 0)).thenReturn(List.of());

        List<PaymentResponseDTO> results = service.listPayments("nonexistent", null, 20, 0);

        assertTrue(results.isEmpty());
    }

    @Test
    void getPayment_withInvalidUUID_throwsNotFoundException() {
        PaymentNotFoundException ex = assertThrows(
            PaymentNotFoundException.class,
            () -> service.getPayment("not-a-valid-uuid")
        );
        assertEquals("not-a-valid-uuid", ex.getPaymentId());
    }

    @Test
    void getPayment_withNull_throwsNotFoundException() {
        PaymentNotFoundException ex = assertThrows(
            PaymentNotFoundException.class,
            () -> service.getPayment(null)
        );
        assertNotNull(ex.getPaymentId());
    }

    @Test
    void listPayments_withInvalidStatus_returnsAllPayments() {
        assertThrows(IllegalArgumentException.class,
            () -> service.listPayments(null, "TYPO_INVALID", 20, 0));
    }
}
