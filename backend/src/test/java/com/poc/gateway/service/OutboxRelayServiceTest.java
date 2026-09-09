package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.entity.OutboxEventEntity;
import com.poc.gateway.entity.OutboxStatus;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.gateway.repository.OutboxEventRepository;
import com.poc.gateway.repository.PaymentRepository;
import com.poc.shared.dto.PaymentMetadataDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    OutboxEventRepository outbox;

    @Mock
    PaymentRepository payments;

    @Mock
    KafkaEventPublisher kafkaEventPublisher;

    @Mock
    PaymentMapper paymentMapper;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    OutboxRelayService relay;

    private Payment payment;
    private OutboxEventEntity pendingEvent;

    @BeforeEach
    void setUp() {
        lenient().when(paymentMapper.toMetadataDTO(any())).thenReturn(PaymentMetadataDTO.empty());

        payment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING, null, null,
            PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        pendingEvent = new OutboxEventEntity();
        pendingEvent.id = UUID.randomUUID();
        pendingEvent.aggregateId = payment.id();
        pendingEvent.type = "payments.events.received";
        pendingEvent.payload = "{\"paymentId\":\"" + payment.id() + "\"}";
        pendingEvent.status = OutboxStatus.PENDING;
        pendingEvent.createdAt = LocalDateTime.now();
        pendingEvent.idempotencyKey = "key-1";
    }

    private void invokeRelayPending() throws Exception {
        var m = OutboxRelayService.class.getDeclaredMethod("relayPending");
        m.setAccessible(true);
        m.invoke(relay);
    }

    // ========== relayPending ==========

    @Test
    @DisplayName("relayPending sends pending events and marks them sent")
    void relayPending_success_marksSent() throws Exception {
        when(outbox.findPending(50)).thenReturn(List.of(pendingEvent));
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of("paymentId", payment.id().toString()));
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
        when(kafkaEventPublisher.publishPaymentReceived(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        invokeRelayPending();

        verify(outbox).markSent(pendingEvent.id);
    }

    @Test
    @DisplayName("relayPending skips when DB unavailable")
    void relayPending_dbDown_returns() throws Exception {
        when(outbox.findPending(50)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(this::invokeRelayPending);
        verify(outbox, never()).markSent(any());
    }

    @Test
    @DisplayName("relayPending does not mark failed publishes")
    void relayPending_publishFalse_noMark() throws Exception {
        when(outbox.findPending(50)).thenReturn(List.of(pendingEvent));
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of("paymentId", payment.id().toString()));
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
        when(kafkaEventPublisher.publishPaymentReceived(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);

        invokeRelayPending();

        verify(outbox, never()).markSent(any());
    }

    @Test
    @DisplayName("relayPending continues when markSent throws")
    void relayPending_markSentThrows_continues() throws Exception {
        when(outbox.findPending(50)).thenReturn(List.of(pendingEvent));
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of("paymentId", payment.id().toString()));
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
        when(kafkaEventPublisher.publishPaymentReceived(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        doThrow(new RuntimeException("mark failed")).when(outbox).markSent(pendingEvent.id);

        assertDoesNotThrow(this::invokeRelayPending);
    }

    @Test
    @DisplayName("relayPending handles empty list")
    void relayPending_empty_noOp() throws Exception {
        when(outbox.findPending(50)).thenReturn(List.of());

        invokeRelayPending();

        verify(outbox, never()).markSent(any());
    }

    // ========== republish ==========

    @Test
    @DisplayName("republish returns true and skips publish when payment missing")
    void republish_paymentMissing_true() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of());
        when(payments.findById(payment.id())).thenReturn(Optional.empty());

        assertTrue(relay.republish(pendingEvent));
        verify(kafkaEventPublisher, never()).publishPaymentReceived(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("republish falls back to aggregateId when payload lacks paymentId")
    void republish_noPaymentIdInPayload_usesAggregate() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of());
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
        when(kafkaEventPublisher.publishPaymentReceived(eq(payment.id().toString()), any(), any(), any(), any(), any(), any()))
            .thenReturn(true);

        assertTrue(relay.republish(pendingEvent));
    }

    @Test
    @DisplayName("republish returns false on serialization failure")
    void republish_badPayload_false() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("bad json"));

        assertFalse(relay.republish(pendingEvent));
    }

    @Test
    @DisplayName("republish returns false when publisher throws")
    void republish_publisherThrows_false() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of("paymentId", payment.id().toString()));
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
        when(kafkaEventPublisher.publishPaymentReceived(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("kafka down"));

        assertFalse(relay.republish(pendingEvent));
    }
}
