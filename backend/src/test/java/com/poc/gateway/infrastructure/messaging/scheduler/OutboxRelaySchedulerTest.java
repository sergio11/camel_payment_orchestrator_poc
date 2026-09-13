package com.poc.gateway.infrastructure.messaging.scheduler;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
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

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        JsonNode amountNode = mock(JsonNode.class);
        JsonNode currencyNode = mock(JsonNode.class);
        JsonNode customerIdNode = mock(JsonNode.class);
        JsonNode paymentMethodNode = mock(JsonNode.class);
        JsonNode countryNode = mock(JsonNode.class);

        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn(paymentId);
        when(root.has("amount")).thenReturn(true);
        when(root.get("amount")).thenReturn(amountNode);
        when(amountNode.asText()).thenReturn("100.00");
        when(root.has("currency")).thenReturn(true);
        when(root.get("currency")).thenReturn(currencyNode);
        when(currencyNode.asText()).thenReturn("USD");
        when(root.has("customerId")).thenReturn(true);
        when(root.get("customerId")).thenReturn(customerIdNode);
        when(customerIdNode.asText()).thenReturn("cust-1");
        when(root.has("paymentMethod")).thenReturn(true);
        when(root.get("paymentMethod")).thenReturn(paymentMethodNode);
        when(paymentMethodNode.asText()).thenReturn("CARD");
        when(root.has("country")).thenReturn(true);
        when(root.get("country")).thenReturn(countryNode);
        when(countryNode.asText()).thenReturn("US");

        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        scheduler.processPendingEvents();

        verify(eventPublisher).publishPaymentReceived(any(PaymentReceivedEvent.class));
        verify(outboxRepo).markSent(event.id());
    }

    @Test
    void processPendingEvents_whenPublishFails_doesNotMarkSent() throws Exception {
        String payload = "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"amount\":\"100.00\",\"currency\":\"USD\",\"customerId\":\"cust-1\",\"paymentMethod\":\"CARD\",\"country\":\"US\"}";
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "payments.events.received", payload, "key-1");

        when(outboxRepo.findPending(50)).thenReturn(List.of(event));

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        JsonNode amountNode = mock(JsonNode.class);
        JsonNode currencyNode = mock(JsonNode.class);
        JsonNode customerIdNode = mock(JsonNode.class);
        JsonNode paymentMethodNode = mock(JsonNode.class);
        JsonNode countryNode = mock(JsonNode.class);

        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn("test-payment-id");
        when(root.has("amount")).thenReturn(true);
        when(root.get("amount")).thenReturn(amountNode);
        when(amountNode.asText()).thenReturn("100.00");
        when(root.has("currency")).thenReturn(true);
        when(root.get("currency")).thenReturn(currencyNode);
        when(currencyNode.asText()).thenReturn("USD");
        when(root.has("customerId")).thenReturn(true);
        when(root.get("customerId")).thenReturn(customerIdNode);
        when(customerIdNode.asText()).thenReturn("cust-1");
        when(root.has("paymentMethod")).thenReturn(true);
        when(root.get("paymentMethod")).thenReturn(paymentMethodNode);
        when(paymentMethodNode.asText()).thenReturn("CARD");
        when(root.has("country")).thenReturn(true);
        when(root.get("country")).thenReturn(countryNode);
        when(countryNode.asText()).thenReturn("US");

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

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        JsonNode amountNode = mock(JsonNode.class);
        JsonNode currencyNode = mock(JsonNode.class);
        JsonNode customerIdNode = mock(JsonNode.class);
        JsonNode paymentMethodNode = mock(JsonNode.class);
        JsonNode countryNode = mock(JsonNode.class);
        JsonNode metadataNode = mock(JsonNode.class);

        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn(paymentId);
        when(root.has("amount")).thenReturn(true);
        when(root.get("amount")).thenReturn(amountNode);
        when(amountNode.asText()).thenReturn("100.00");
        when(root.has("currency")).thenReturn(true);
        when(root.get("currency")).thenReturn(currencyNode);
        when(currencyNode.asText()).thenReturn("USD");
        when(root.has("customerId")).thenReturn(true);
        when(root.get("customerId")).thenReturn(customerIdNode);
        when(customerIdNode.asText()).thenReturn("cust-1");
        when(root.has("paymentMethod")).thenReturn(true);
        when(root.get("paymentMethod")).thenReturn(paymentMethodNode);
        when(paymentMethodNode.asText()).thenReturn("CARD");
        when(root.has("country")).thenReturn(true);
        when(root.get("country")).thenReturn(countryNode);
        when(countryNode.asText()).thenReturn("US");
        when(root.has("metadata")).thenReturn(true);
        when(root.get("metadata")).thenReturn(metadataNode);
        when(metadataNode.isNull()).thenReturn(false);
        when(metadataNode.isMissingNode()).thenReturn(false);

        PaymentMetadata expectedMetadata = new PaymentMetadata("ord-1", 3, null, null, null, null, null, null);
        when(objectMapper.convertValue(metadataNode, PaymentMetadata.class)).thenReturn(expectedMetadata);
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

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        JsonNode amountNode = mock(JsonNode.class);
        JsonNode currencyNode = mock(JsonNode.class);
        JsonNode customerIdNode = mock(JsonNode.class);
        JsonNode paymentMethodNode = mock(JsonNode.class);
        JsonNode countryNode = mock(JsonNode.class);
        JsonNode metadataNode = mock(JsonNode.class);

        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn("pay-1");
        when(root.has("amount")).thenReturn(true);
        when(root.get("amount")).thenReturn(amountNode);
        when(amountNode.asText()).thenReturn("10.00");
        when(root.has("currency")).thenReturn(true);
        when(root.get("currency")).thenReturn(currencyNode);
        when(currencyNode.asText()).thenReturn("USD");
        when(root.has("customerId")).thenReturn(true);
        when(root.get("customerId")).thenReturn(customerIdNode);
        when(customerIdNode.asText()).thenReturn("c");
        when(root.has("paymentMethod")).thenReturn(true);
        when(root.get("paymentMethod")).thenReturn(paymentMethodNode);
        when(paymentMethodNode.asText()).thenReturn("CARD");
        when(root.has("country")).thenReturn(true);
        when(root.get("country")).thenReturn(countryNode);
        when(countryNode.asText()).thenReturn("US");
        when(root.has("metadata")).thenReturn(true);
        when(root.get("metadata")).thenReturn(metadataNode);
        when(metadataNode.isNull()).thenReturn(true);

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

        JsonNode root = mock(JsonNode.class);
        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(root.has("paymentId")).thenReturn(false);
        when(root.has("amount")).thenReturn(false);
        when(root.has("currency")).thenReturn(false);
        when(root.has("customerId")).thenReturn(false);
        when(root.has("paymentMethod")).thenReturn(false);
        when(root.has("country")).thenReturn(false);

        when(eventPublisher.publishPaymentReceived(any(PaymentReceivedEvent.class))).thenReturn(true);

        scheduler.processPendingEvents();

        verify(eventPublisher).publishPaymentReceived(any(PaymentReceivedEvent.class));
        verify(outboxRepo).markSent(event.id());
    }
}
