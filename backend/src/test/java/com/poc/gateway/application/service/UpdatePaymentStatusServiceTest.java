package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.outbound.PaymentWriteRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdatePaymentStatusServiceTest {

    @Mock
    PaymentWriteRepository paymentRepo;

    @Mock
    PaymentMapper mapper;

    @InjectMocks
    UpdatePaymentStatusService service;

    @Test
    void execute_updatesPaymentStatus() {
        String id = UUID.randomUUID().toString();
        Payment payment = new Payment(
            UUID.fromString(id), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        Payment updated = payment.withStatus(PaymentStatus.APPROVED);

        when(paymentRepo.update(UUID.fromString(id), PaymentStatus.APPROVED)).thenReturn(updated);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(id, BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "APPROVED", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentResponseDTO result = service.execute(id, "APPROVED");

        assertNotNull(result);
        assertEquals("APPROVED", result.status());
    }

    @Test
    void execute_throwsExceptionForInvalidStatus() {
        String id = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () -> service.execute(id, "TYPO"));
    }

    @Test
    void execute_invalidUuid_throwsPaymentNotFoundException() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute("not-a-uuid", "APPROVED"));
    }

    @Test
    void execute_nullId_throwsPaymentNotFoundException() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute(null, "APPROVED"));
    }
}
