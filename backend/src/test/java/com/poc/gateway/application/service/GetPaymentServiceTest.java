package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
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

    @InjectMocks
    GetPaymentService service;

    private Payment buildPayment(UUID id) {
        return new Payment(
            id, new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void execute_returnsPaymentForValidId() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id);

        when(paymentRepo.findById(id)).thenReturn(Optional.of(payment));

        Payment result = service.execute(id);

        assertNotNull(result);
        assertEquals(id, result.id());
        verify(paymentRepo).findById(id);
    }

    @Test
    void execute_throwsPaymentNotFoundExceptionForNullId() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute(null));
    }

    @Test
    void execute_throwsPaymentNotFoundExceptionWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(paymentRepo.findById(id)).thenReturn(Optional.empty());

        PaymentNotFoundException ex = assertThrows(PaymentNotFoundException.class, () -> service.execute(id));
        assertEquals(id.toString(), ex.getPaymentId());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyForNull() {
        Optional<Payment> result = service.executeByIdempotencyKey(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyForBlank() {
        Optional<Payment> result = service.executeByIdempotencyKey("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyForEmptyString() {
        Optional<Payment> result = service.executeByIdempotencyKey("");
        assertTrue(result.isEmpty());
    }

    @Test
    void executeByIdempotencyKey_returnsEmptyWhenNotFound() {
        when(paymentRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.empty());

        Optional<Payment> result = service.executeByIdempotencyKey("idem-123");
        assertTrue(result.isEmpty());
        verify(paymentRepo).findByIdempotencyKey("idem-123");
    }

    @Test
    void executeByIdempotencyKey_returnsPaymentWhenFound() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id);

        when(paymentRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.of(payment));

        Optional<Payment> result = service.executeByIdempotencyKey("idem-123");
        assertTrue(result.isPresent());
        assertEquals(id, result.get().id());
    }

    @Test
    void executeByIdempotencyKey_trimsWhitespace() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id);

        when(paymentRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.of(payment));

        Optional<Payment> result = service.executeByIdempotencyKey("  idem-123  ");
        assertTrue(result.isPresent());
        verify(paymentRepo).findByIdempotencyKey("idem-123");
    }
}
