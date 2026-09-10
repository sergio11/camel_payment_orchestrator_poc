package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentPageResponseDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListPaymentsServiceTest {

    @Mock
    PaymentReadRepository paymentRepo;

    @Mock
    PaymentMapper mapper;

    @InjectMocks
    ListPaymentsService service;

    @Test
    void execute_returnsPaymentsWithNoFilters() {
        Payment payment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(paymentRepo.findAll(null, null, 20, 0)).thenReturn(List.of(payment));
        when(paymentRepo.count(null, null)).thenReturn(1L);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(
            new PaymentResponseDTO(payment.id().toString(), BigDecimal.TEN, "USD", "cust-1",
                "CREDIT_CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now())
        );

        PaymentPageResponseDTO result = service.execute(null, null, 20, 0);

        assertNotNull(result);
        assertEquals(1, result.payments().size());
    }

    @Test
    void execute_convertsStatusStringToEnum() {
        when(paymentRepo.findAll(eq("cust-1"), eq(PaymentStatus.APPROVED), anyInt(), anyInt()))
            .thenReturn(List.of());
        when(paymentRepo.count(eq("cust-1"), eq(PaymentStatus.APPROVED))).thenReturn(0L);

        service.execute("cust-1", "APPROVED", 20, 0);

        verify(paymentRepo).findAll("cust-1", PaymentStatus.APPROVED, 20, 0);
    }
}
