package com.poc.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    ObjectMapper objectMapper;

    @Mock
    KafkaProducer<String, String> producer;

    @InjectMocks
    KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        publisher.bootstrapServers = "localhost:9092";
        publisher.paymentsReceivedTopic = "payments.received";
        publisher.statusChangedTopic = "payments.status.changed";

        setProducer(producer);
    }

    private void setProducer(KafkaProducer<String, String> p) throws Exception {
        Field field = KafkaEventPublisher.class.getDeclaredField("producer");
        field.setAccessible(true);
        field.set(publisher, p);
    }

    private RecordMetadata createRecordMetadata() {
        return new RecordMetadata(
            new TopicPartition("payments.received", 0),
            0L, 0L, 0L, 0L, 0, 0
        );
    }

    @Test
    @DisplayName("publishPaymentReceived - happy path returns true")
    void publishPaymentReceived_happyPath_returnsTrue() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        assertTrue(result);
        verify(producer).send(any(ProducerRecord.class), any());
    }

    @Test
    @DisplayName("publishPaymentReceived - ExecutionException wrapped TimeoutException returns false")
    void publishPaymentReceived_timeout_returnsFalse() throws Exception {
        CompletableFuture<RecordMetadata> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new TimeoutException("Kafka timed out"));
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(timeoutFuture);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("publishPaymentReceived - real TimeoutException from .get() returns false")
    void publishPaymentReceived_realTimeout_returnsFalse() throws Exception {
        CompletableFuture<RecordMetadata> blockingFuture = new CompletableFuture<>() {
            @Override
            public RecordMetadata get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                throw new TimeoutException("real timeout");
            }
        };
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(blockingFuture);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("publishPaymentReceived - execution exception returns false")
    void publishPaymentReceived_executionException_returnsFalse() throws Exception {
        CompletableFuture<RecordMetadata> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new ExecutionException("kafka down", new RuntimeException()));
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(failedFuture);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("publishPaymentReceived - serialization error returns false")
    void publishPaymentReceived_serializationError_returnsFalse() throws Exception {
        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("bad") {});

        boolean result = publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        assertFalse(result);
        verify(producer, never()).send(any(ProducerRecord.class), any());
    }

    @Test
    @DisplayName("publishPaymentReceived - callback invoked with success logs info")
    void publishPaymentReceived_callbackSuccess_logsInfo() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        verify(producer).send(any(ProducerRecord.class), callbackCaptor.capture());
        Callback capturedCallback = callbackCaptor.getValue();
        capturedCallback.onCompletion(metadata, null);
    }

    @Test
    @DisplayName("publishPaymentReceived - callback invoked with exception logs error")
    void publishPaymentReceived_callbackException_logsError() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishPaymentReceived(
            "pay-123", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        verify(producer).send(any(ProducerRecord.class), callbackCaptor.capture());
        Callback capturedCallback = callbackCaptor.getValue();
        capturedCallback.onCompletion(null, new RuntimeException("broker down"));
    }

    @Test
    @DisplayName("publishStatusChanged - happy path completes without exception")
    void publishStatusChanged_happyPath_noException() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertDoesNotThrow(() ->
            publisher.publishStatusChanged("pay-123", "PENDING", "APPROVED")
        );
        verify(producer).send(any(ProducerRecord.class), any());
    }

    @Test
    @DisplayName("publishStatusChanged - serialization error is caught and logged")
    void publishStatusChanged_serializationError_noExceptionThrown() throws Exception {
        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("bad") {});

        assertDoesNotThrow(() ->
            publisher.publishStatusChanged("pay-123", "PENDING", "APPROVED")
        );
        verify(producer, never()).send(any(ProducerRecord.class), any());
    }

    @Test
    @DisplayName("publishStatusChanged - callback invoked with success logs info")
    void publishStatusChanged_callbackSuccess_logsInfo() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishStatusChanged("pay-123", "PENDING", "APPROVED");

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        verify(producer).send(any(ProducerRecord.class), callbackCaptor.capture());
        Callback capturedCallback = callbackCaptor.getValue();
        capturedCallback.onCompletion(metadata, null);
    }

    @Test
    @DisplayName("publishStatusChanged - callback invoked with exception logs error")
    void publishStatusChanged_callbackException_logsError() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publishStatusChanged("pay-123", "PENDING", "APPROVED");

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        verify(producer).send(any(ProducerRecord.class), callbackCaptor.capture());
        Callback capturedCallback = callbackCaptor.getValue();
        capturedCallback.onCompletion(null, new RuntimeException("broker down"));
    }

    @Test
    @DisplayName("cleanup closes producer when not null")
    void cleanup_closesProducer() throws Exception {
        publisher.cleanup();

        verify(producer).close();
    }

    @Test
    @DisplayName("cleanup does nothing when producer is null")
    void cleanup_nullProducer_noException() throws Exception {
        setProducer(null);

        assertDoesNotThrow(() -> publisher.cleanup());
    }

    @Test
    @DisplayName("publishPaymentReceived - lazy init when producer is null fails gracefully")
    void publishPaymentReceived_nullProducer_lazyInitFails() throws Exception {
        setProducer(null);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-456", new BigDecimal("250.00"), "EUR",
            "cust-2", "CREDIT_CARD", "DE", Map.of()
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("publishPaymentReceived - producer already initialized, uses existing")
    void publishPaymentReceived_existingProducer_usesExisting() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-abc", new BigDecimal("10.00"), "USD",
            "cust-x", "CREDIT_CARD", "US", Map.of()
        );

        assertTrue(result);
        verify(producer).send(any(ProducerRecord.class), any());
        Field producerField = KafkaEventPublisher.class.getDeclaredField("producer");
        producerField.setAccessible(true);
        KafkaProducer<String, String> after = (KafkaProducer<String, String>) producerField.get(publisher);
        assertSame(producer, after);
    }

    @Test
    @DisplayName("publishPaymentReceived - send returns failed future")
    void publishPaymentReceived_sendFails_returnsFalse() throws Exception {
        CompletableFuture<RecordMetadata> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException(" broker unavailable"));
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(failedFuture);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = publisher.publishPaymentReceived(
            "pay-xyz", new BigDecimal("50.00"), "USD",
            "cust-3", "DEBIT_CARD", "US", Map.of()
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("publishStatusChanged - lazy init when producer is null fails gracefully")
    void publishStatusChanged_nullProducer_lazyInitFails() throws Exception {
        setProducer(null);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertDoesNotThrow(() ->
            publisher.publishStatusChanged("pay-789", "PENDING", "APPROVED")
        );
    }

    @Test
    @DisplayName("publishStatusChanged - producer already initialized, uses existing")
    void publishStatusChanged_existingProducer_usesExisting() throws Exception {
        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertDoesNotThrow(() ->
            publisher.publishStatusChanged("pay-789", "PENDING", "APPROVED")
        );
        verify(producer).send(any(ProducerRecord.class), any());
        Field producerField = KafkaEventPublisher.class.getDeclaredField("producer");
        producerField.setAccessible(true);
        KafkaProducer<String, String> after = (KafkaProducer<String, String>) producerField.get(publisher);
        assertSame(producer, after);
    }

    @Test
    @DisplayName("getProducer - double-checked locking skips init when already set")
    void getProducer_alreadyInitialized_doesNotRecreate() throws Exception {
        Field producerField = KafkaEventPublisher.class.getDeclaredField("producer");
        producerField.setAccessible(true);

        KafkaEventPublisher realPublisher = new KafkaEventPublisher();
        realPublisher.bootstrapServers = "localhost:9092";
        realPublisher.paymentsReceivedTopic = "payments.received";
        realPublisher.statusChangedTopic = "payments.status.changed";
        realPublisher.objectMapper = objectMapper;

        KafkaProducer<String, String> existingProducer = mock(KafkaProducer.class);
        producerField.set(realPublisher, existingProducer);

        RecordMetadata metadata = createRecordMetadata();
        CompletableFuture<RecordMetadata> future = CompletableFuture.completedFuture(metadata);
        when(existingProducer.send(any(ProducerRecord.class), any())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        realPublisher.publishPaymentReceived(
            "pay-abc", new BigDecimal("10.00"), "USD",
            "cust-x", "CARD", "US", Map.of()
        );

        verify(existingProducer).send(any(ProducerRecord.class), any());
        KafkaProducer<String, String> after = (KafkaProducer<String, String>) producerField.get(realPublisher);
        assertSame(existingProducer, after);
    }

}
