package com.poc.gateway.resource;

import com.poc.gateway.service.PaymentService;
import com.poc.gateway.resource.presenter.PaymentResponsePresenter;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentResourcePrecheckTest {

    @Mock
    PaymentService paymentService;

    @Mock
    PaymentResponsePresenter presenter;

    private PaymentResource resource() {
        PaymentResource r = new PaymentResource();
        r.paymentService = paymentService;
        r.presenter = presenter;
        return r;
    }

    private PaymentRequestDTO request() {
        return new PaymentRequestDTO(new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", null);
    }

    private PaymentResponseDTO response(String id) {
        return new PaymentResponseDTO(id, new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD",
            "US", "PENDING", null, null, Map.of(), LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("pre-check failure still creates payment (best-effort)")
    void precheckThrows_stillCreates() {
        when(paymentService.getByIdempotencyKey(anyString())).thenThrow(new RuntimeException("db down"));
        when(paymentService.createPayment(any(PaymentRequestDTO.class), anyString()))
            .thenReturn(response("pay-1"));
        when(presenter.paymentCreated(any(), anyString(), eq(false)))
            .thenReturn(Response.status(201).entity(response("pay-1")).build());

        Response r = resource().createPayment(request(), null);

        assertEquals(201, r.getStatus());
        assertEquals("pay-1", ((PaymentResponseDTO) r.getEntity()).id());
    }

    @Test
    @DisplayName("replay returns 200 with existing payment")
    void replay_returns200() {
        PaymentResponseDTO existing = response("pay-9");
        when(paymentService.getByIdempotencyKey(anyString())).thenReturn(Optional.of(existing));
        when(paymentService.createPayment(any(PaymentRequestDTO.class), anyString())).thenReturn(existing);
        when(presenter.paymentCreated(any(), anyString(), eq(true)))
            .thenReturn(Response.status(200).entity(existing).build());

        Response r = resource().createPayment(request(), UUID.randomUUID().toString());

        assertEquals(200, r.getStatus());
    }

    @Test
    @DisplayName("invalid idempotency key returns 400")
    void invalidKey_returns400() {
        when(presenter.invalidIdempotencyKey())
            .thenReturn(Response.status(400).build());

        Response r = resource().createPayment(request(), "not-a-uuid");
        assertEquals(400, r.getStatus());
        verify(paymentService, never()).createPayment(any(), anyString());
    }

    @Test
    @DisplayName("blank idempotency key is generated")
    void blankKey_generated() {
        when(paymentService.getByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentService.createPayment(any(PaymentRequestDTO.class), anyString()))
            .thenReturn(response("pay-2"));
        when(presenter.paymentCreated(any(), anyString(), eq(false)))
            .thenReturn(Response.status(201).header("Idempotency-Key", "generated").entity(response("pay-2")).build());

        Response r = resource().createPayment(request(), "   ");

        assertEquals(201, r.getStatus());
        assertNotNull(r.getHeaderString("Idempotency-Key"));
    }
}
