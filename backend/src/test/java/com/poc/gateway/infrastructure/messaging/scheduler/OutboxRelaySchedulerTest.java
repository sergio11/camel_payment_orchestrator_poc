package com.poc.gateway.infrastructure.messaging.scheduler;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    OutboxRepositoryPort outboxRepo;

    @Mock
    EventPublisherPort eventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler.enabled = true;
        scheduler.batchSize = 50;
    }

    @Test
    void processPendingEvents_whenEmpty_doesNothing() {
        when(outboxRepo.findPending(50)).thenReturn(Collections.emptyList());

        scheduler.processPendingEvents();

        verifyNoInteractions(eventPublisher);
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void processPendingEvents_withValidEvent_publishesAndMarksSent() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String payload = String.format(
            "{\"paymentId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"customerId\":\"cust-1\",\"paymentMethod\":\"CARD\",\"country\":\"US\"}",
            paymentId
        );
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "payments.events.received", payload, "key-1");

        when(outboxRepo.findPending(50)).thenReturn(List.of(event));

        PaymentReceivedEvent expectedEvent = new PaymentReceivedEvent(
            paymentId, new java.math.BigDecimal("100.00"), "USD", "cust-1", "CARD", "US", null
        );
        when(objectMapper.readValue(eq(payload), eq(PaymentReceivedEvent.class))).thenReturn(expectedEvent);
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        scheduler.processPendingEvents();

        verify(eventPublisher).publishPaymentReceived(expectedEvent);
        verify(outboxRepo).markSent(event.id());
    }

    @Test
    void processPendingEvents_whenPublishFails_doesNotMarkSent() throws Exception {
        String payload = "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"amount\":\"100.00\",\"currency\":\"USD\",\"customerId\":\"cust-1\",\"paymentMethod\":\"CARD\",\"country\":\"US\"}";
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "payments.events.received", payload, "key-1");

        when(outboxRepo.findPending(50)).thenReturn(List.of(event));

        PaymentReceivedEvent expectedEvent = new PaymentReceivedEvent(
            "test-payment-id", new java.math.BigDecimal("100.00"), "USD", "cust-1", "CARD", "US", null
        );
        when(objectMapper.readValue(eq(payload), eq(PaymentReceivedEvent.class))).thenReturn(expectedEvent);
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenThrow(new RuntimeException("Kafka down"));

        scheduler.processPendingEvents();

        verify(outboxRepo, never()).markSent(event.id());
    }

    @Test
    void processPendingEvents_whenDisabled_doesNothing() {
        scheduler.enabled = false;

        scheduler.processPendingEvents();

        verifyNoInteractions(outboxRepo);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void processPendingEvents_withMetadata_deserializesAndPublishes() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String payload = String.format(
            "{\"paymentId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"customerId\":\"cust-1\",\"paymentMethod\":\"CARD\",\"country\":\"US\",\"metadata\":{\"orderId\":\"ord-1\",\"attempts\":3}}",
            paymentId
        );
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "payments.events.received", payload, "key-1");

        when(outboxRepo.findPending(50)).thenReturn(List.of(event));

        PaymentMetadata expectedMetadata = new PaymentMetadata("ord-1", 3, null, null, null, null, null, null);
        PaymentReceivedEvent expectedEvent = new PaymentReceivedEvent(
            paymentId, new java.math.BigDecimal("100.00"), "USD", "cust-1", "CARD", "US", expectedMetadata
        );
        when(objectMapper.readValue(eq(payload), eq(PaymentReceivedEvent.class))).thenReturn(expectedEvent);
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        scheduler.processPendingEvents();

        ArgumentCaptor<PaymentReceivedEvent> captor = ArgumentCaptor.forClass(PaymentReceivedEvent.class);
        verify(eventPublisher).publishPaymentReceived(captor.capture());
        PaymentReceivedEvent published = captor.getValue();
        org.junit.jupiter.api.Assertions.assertNotNull(published.metadata());
        org.junit.jupiter.api.Assertions.assertEquals("ord-1", published.metadata().orderId());
        verify(outboxRepo).markSent(event.id());
    }

    @Test
    void processPendingEvents_withNullJsonMetadata_usesNullMetadata() throws Exception {
        String payload = "{\"paymentId\":\"pay-1\",\"amount\":\"10.00\",\"currency\":\"USD\",\"customerId\":\"c\",\"paymentMethod\":\"CARD\",\"country\":\"US\",\"metadata\":null}";
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "payments.events.received", payload, "key-1");

        when(outboxRepo.findPending(50)).thenReturn(List.of(event));

        PaymentReceivedEvent expectedEvent = new PaymentReceivedEvent(
            "pay-1", new java.math.BigDecimal("10.00"), "USD", "c", "CARD", "US", null
        );
        when(objectMapper.readValue(eq(payload), eq(PaymentReceivedEvent.class))).thenReturn(expectedEvent);
        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        scheduler.processPendingEvents();

        ArgumentCaptor<PaymentReceivedEvent> captor = ArgumentCaptor.forClass(PaymentReceivedEvent.class);
        verify(eventPublisher).publishPaymentReceived(captor.capture());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().metadata());
        verify(outboxRepo).markSent(event.id());
    }

    @Test
    void processPendingEvents_withIncompletePayload_usesFallbackValues() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        String payload = "{}";
        OutboxEvent event = OutboxEvent.create(aggregateId, "payments.events.received", payload, "key-1");

        when(outboxRepo.findPending(50)).thenReturn(List.of(event));

        when(objectMapper.readValue(eq(payload), eq(PaymentReceivedEvent.class)))
            .thenThrow(new RuntimeException("Deserialization failed"));

        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        scheduler.processPendingEvents();

        verify(eventPublisher).publishPaymentReceived(any(PaymentReceivedEvent.class));
        verify(outboxRepo).markSent(event.id());
    }
}
