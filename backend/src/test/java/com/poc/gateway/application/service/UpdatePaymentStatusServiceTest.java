package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.outbound.PaymentWriteRepository;
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
class UpdatePaymentStatusServiceTest {

    @Mock
    PaymentWriteRepository paymentRepo;

    @InjectMocks
    UpdatePaymentStatusService service;

    @Test
    void execute_updatesPaymentStatus() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment(
            id, new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        Payment updated = payment.withStatus(PaymentStatus.PROCESSING);

        when(paymentRepo.update(id, PaymentStatus.PROCESSING)).thenReturn(updated);

        Payment result = service.execute(id, PaymentStatus.PROCESSING);

        assertNotNull(result);
        assertEquals(PaymentStatus.PROCESSING, result.status());
    }

    @Test
    void execute_nullId_throwsPaymentNotFoundException() {
        assertThrows(PaymentNotFoundException.class, () -> service.execute(null, PaymentStatus.APPROVED));
    }
}
