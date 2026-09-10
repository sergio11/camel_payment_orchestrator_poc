package com.poc.gateway.infrastructure.messaging.consumer;

import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentWriteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentStatusHandlerTest {

    @Mock
    PaymentWriteRepository paymentRepo;

    @Mock
    EventPublisherPort eventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    PaymentStatusHandler handler;

    @Test
    void processStatusChange_updatesAndPublishes() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, paymentId, "{\"paymentId\":\"" + paymentId + "\"}");

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        doReturn(root).when(objectMapper).readTree(anyString());
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn(paymentId);

        when(paymentRepo.updateIfPending(UUID.fromString(paymentId), PaymentStatus.APPROVED))
            .thenReturn(Optional.empty());

        handler.processStatusChange(record, PaymentStatus.APPROVED);

        verify(paymentRepo).updateIfPending(UUID.fromString(paymentId), PaymentStatus.APPROVED);
        verify(eventPublisher).publishStatusChanged(paymentId, "APPROVED");
    }

    @Test
    void processStatusChange_exception_sendsToDlq() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, paymentId, "{\"paymentId\":\"" + paymentId + "\"}");

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        doReturn(root).when(objectMapper).readTree(anyString());
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn(paymentId);

        doThrow(new RuntimeException("fail")).when(paymentRepo).updateIfPending(any(), any());

        handler.processStatusChange(record, PaymentStatus.APPROVED);

        verify(eventPublisher).publishDeadLetter(eq(paymentId), anyString(), anyString());
        verify(eventPublisher, never()).publishStatusChanged(anyString(), anyString());
    }

    @Test
    void processStatusChange_noPaymentIdInJson_usesKey() throws Exception {
        String fallbackKey = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, fallbackKey, "{}");

        JsonNode root = mock(JsonNode.class);
        doReturn(root).when(objectMapper).readTree(anyString());
        when(root.has("paymentId")).thenReturn(false);

        when(paymentRepo.updateIfPending(UUID.fromString(fallbackKey), PaymentStatus.APPROVED))
            .thenReturn(Optional.empty());

        handler.processStatusChange(record, PaymentStatus.APPROVED);

        verify(paymentRepo).updateIfPending(UUID.fromString(fallbackKey), PaymentStatus.APPROVED);
        verify(eventPublisher).publishStatusChanged(fallbackKey, "APPROVED");
    }

    @Test
    void processStatusChange_invalidJson_usesKey() throws Exception {
        String fallbackKey = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, fallbackKey, "not json");

        doThrow(new RuntimeException("Invalid JSON")).when(objectMapper).readTree(anyString());

        when(paymentRepo.updateIfPending(UUID.fromString(fallbackKey), PaymentStatus.FAILED))
            .thenReturn(Optional.empty());

        handler.processStatusChange(record, PaymentStatus.FAILED);

        verify(paymentRepo).updateIfPending(UUID.fromString(fallbackKey), PaymentStatus.FAILED);
        verify(eventPublisher).publishStatusChanged(fallbackKey, "FAILED");
    }

    @Test
    void processStatusChange_noPaymentIdField_usesKey() throws Exception {
        String key = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, key, "{\"other\":\"data\"}");

        JsonNode root = mock(JsonNode.class);
        doReturn(root).when(objectMapper).readTree(anyString());
        when(root.has("paymentId")).thenReturn(false);

        when(paymentRepo.updateIfPending(UUID.fromString(key), PaymentStatus.REJECTED))
            .thenReturn(Optional.empty());

        handler.processStatusChange(record, PaymentStatus.REJECTED);

        verify(paymentRepo).updateIfPending(UUID.fromString(key), PaymentStatus.REJECTED);
        verify(eventPublisher).publishStatusChanged(key, "REJECTED");
    }

    @Test
    void processStatusChange_nullKeyAndInvalidJson_sendsToDlq() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, null, "not json");

        doThrow(new RuntimeException("Invalid JSON")).when(objectMapper).readTree(anyString());

        handler.processStatusChange(record, PaymentStatus.APPROVED);

        verify(eventPublisher).publishDeadLetter(isNull(), anyString(), contains("No paymentId"));
        verify(paymentRepo, never()).updateIfPending(any(), any());
    }

    @Test
    void processStatusChange_nullKeyAndNullJson_sendsToDlq() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, null, null);

        doThrow(new RuntimeException("Null input")).when(objectMapper).readTree((String) isNull());

        handler.processStatusChange(record, PaymentStatus.APPROVED);

        verify(eventPublisher).publishDeadLetter(isNull(), any(), contains("No paymentId"));
    }

    @Test
    void sendToDeadLetter_publishesDlq() {
        String key = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, key, "value");
        handler.sendToDeadLetter(record, "reason");
        verify(eventPublisher).publishDeadLetter(eq(key), eq("value"), eq("reason"));
    }

    @Test
    void sendToDeadLetter_nullKey_publishesWithNullPaymentId() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, null, "value");
        handler.sendToDeadLetter(record, "reason");
        verify(eventPublisher).publishDeadLetter(isNull(), eq("value"), eq("reason"));
    }

    @Test
    void sendToDeadLetter_publisherThrows_doesNotPropagate() {
        String key = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, key, "value");
        doThrow(new RuntimeException("pub fail")).when(eventPublisher).publishDeadLetter(any(), any(), any());

        assertDoesNotThrow(() -> handler.sendToDeadLetter(record, "reason"));
    }

    @Test
    void processStatusChange_jsonHasPaymentIdExtractedFromValue() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String json = "{\"paymentId\":\"" + paymentId + "\",\"amount\":100}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "different-key", json);

        JsonNode root = mock(JsonNode.class);
        JsonNode paymentIdNode = mock(JsonNode.class);
        doReturn(root).when(objectMapper).readTree(anyString());
        when(root.has("paymentId")).thenReturn(true);
        when(root.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asText()).thenReturn(paymentId);

        when(paymentRepo.updateIfPending(UUID.fromString(paymentId), PaymentStatus.REVIEW))
            .thenReturn(Optional.empty());

        handler.processStatusChange(record, PaymentStatus.REVIEW);

        verify(paymentRepo).updateIfPending(UUID.fromString(paymentId), PaymentStatus.REVIEW);
    }

    private static void assertDoesNotThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception but got: " + e, e);
        }
    }
}
