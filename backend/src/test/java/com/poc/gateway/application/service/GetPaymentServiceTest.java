package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentResponseDTO;
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
class GetPaymentServiceTest {

    @Mock
    PaymentReadRepository paymentRepo;

    @Mock
    PaymentMapper mapper;

    @InjectMocks
    GetPaymentService service;

    private Payment buildPayment(UUID id) {
        return new Payment(
            id, new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private PaymentResponseDTO buildResponseDto(String id) {
        return new PaymentResponseDTO(id, BigDecimal.TEN, "USD", "cust-1",
            "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void execute_returnsPaymentForValidId() {
        String id = UUID.randomUUID().toString();
        Payment payment = buildPayment(UUID.fromString(id));

        when(paymentRepo.findById(UUID.fromString(id))).thenReturn(Optional.of(payment));
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(buildResponseDto(id));

        PaymentResponseDTO result = service.execute(id);

        assertNotNull(result);
        assertEquals(id, result.id());
        verify(paymentRepo).findById(UUID.fromString(id));
        verify(mapper).toResponseDTO(payment);
    }

    @Test
    void execute_throwsPaymentNotFoundExceptionForNullId() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute(null));
    }

    @Test
    void execute_throwsPaymentNotFoundExceptionForInvalidUuid() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute("not-a-uuid"));
    }

    @Test
    void execute_throwsPaymentNotFoundExceptionForEmptyString() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute(""));
    }

    @Test
    void execute_throwsPaymentNotFoundExceptionWhenNotFound() {
        String id = UUID.randomUUID().toString();
        when(paymentRepo.findById(UUID.fromString(id))).thenReturn(Optional.empty());

        PaymentNotFoundException ex = assertThrows(PaymentNotFoundException.class, () -> service.execute(id));
        assertEquals(id, ex.getPaymentId());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyForNull() {
        Optional<PaymentResponseDTO> result = service.executeByIdempotencyKey(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyForBlank() {
        Optional<PaymentResponseDTO> result = service.executeByIdempotencyKey("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyForEmptyString() {
        Optional<PaymentResponseDTO> result = service.executeByIdempotencyKey("");
        assertTrue(result.isEmpty());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyWhenNotFound() {
        when(paymentRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.empty());

        Optional<PaymentResponseDTO> result = service.executeByIdempotencyKey("idem-123");
        assertTrue(result.isEmpty());
        verify(paymentRepo).findByIdempotencyKey("idem-123");
    }

    @Test
    void executeByIdempotencyKey_returnsResponseWhenFound() {
        String id = UUID.randomUUID().toString();
        Payment payment = buildPayment(UUID.fromString(id));
        PaymentResponseDTO dto = buildResponseDto(id);

        when(paymentRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.of(payment));
        when(mapper.toResponseDTO(payment)).thenReturn(dto);

        Optional<PaymentResponseDTO> result = service.executeByIdempotencyKey("idem-123");
        assertTrue(result.isPresent());
        assertEquals(id, result.get().id());
    }

    @Test
    void executeByIdempotencyKey_trimsWhitespace() {
        String id = UUID.randomUUID().toString();
        Payment payment = buildPayment(UUID.fromString(id));
        PaymentResponseDTO dto = buildResponseDto(id);

        when(paymentRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.of(payment));
        when(mapper.toResponseDTO(payment)).thenReturn(dto);

        Optional<PaymentResponseDTO> result = service.executeByIdempotencyKey("  idem-123  ");
        assertTrue(result.isPresent());
        verify(paymentRepo).findByIdempotencyKey("idem-123");
    }
}
