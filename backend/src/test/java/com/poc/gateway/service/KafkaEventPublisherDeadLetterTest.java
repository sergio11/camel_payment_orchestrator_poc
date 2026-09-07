package com.poc.gateway.service;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherDeadLetterTest {

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
        publisher.deadLetterTopic = "payments.dead-letter";
        setProducer(producer);
    }

    private void setProducer(KafkaProducer<String, String> p) throws Exception {
        Field field = KafkaEventPublisher.class.getDeclaredField("producer");
        field.setAccessible(true);
        field.set(publisher, p);
    }

    private RecordMetadata metadata() {
        return new RecordMetadata(new TopicPartition("payments.dead-letter", 0), 0L, 0L, 0L, 0L, 0, 0);
    }

    // ========== publishDeadLetter ==========

    @Test
    @DisplayName("publishDeadLetter happy path returns true")
    void publishDeadLetter_happyPath_true() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(CompletableFuture.completedFuture(metadata()));

        assertTrue(publisher.publishDeadLetter("pay-1", "payments.processed", "poison", "raw"));
        verify(producer).send(any(ProducerRecord.class), any());
    }

    @Test
    @DisplayName("publishDeadLetter uses unknown key for null paymentId")
    void publishDeadLetter_nullKey_unknown() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(CompletableFuture.completedFuture(metadata()));

        assertTrue(publisher.publishDeadLetter(null, "t", "r", null));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture(), any());
        assertEquals("unknown", captor.getValue().key());
    }

    @Test
    @DisplayName("publishDeadLetter returns false on send failure")
    void publishDeadLetter_sendFails_false() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        CompletableFuture<RecordMetadata> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(failed);

        assertFalse(publisher.publishDeadLetter("pay-1", "t", "r", "raw"));
    }

    @Test
    @DisplayName("publishDeadLetter handles null producer gracefully")
    void publishDeadLetter_nullProducer_false() throws Exception {
        setProducer(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertFalse(publisher.publishDeadLetter("pay-1", "t", "r", "raw"));
    }

    @Test
    @DisplayName("publishDeadLetter callback success and error paths")
    void publishDeadLetter_callbacks_noThrow() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(CompletableFuture.completedFuture(metadata()));

        publisher.publishDeadLetter("pay-1", "t", "r", "raw");

        ArgumentCaptor<Callback> captor = ArgumentCaptor.forClass(Callback.class);
        verify(producer).send(any(ProducerRecord.class), captor.capture());
        Callback cb = captor.getValue();
        assertDoesNotThrow(() -> cb.onCompletion(metadata(), null));
        assertDoesNotThrow(() -> cb.onCompletion(null, new RuntimeException("async fail")));
    }

    // ========== publishStatusChanged timeout ==========

    @Test
    @DisplayName("publishStatusChanged returns false on real TimeoutException")
    void publishStatusChanged_realTimeout_false() throws Exception {
        CompletableFuture<RecordMetadata> blocking = new CompletableFuture<>() {
            @Override
            public RecordMetadata get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                throw new TimeoutException("real timeout");
            }
        };
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(blocking);

        assertDoesNotThrow(() -> publisher.publishStatusChanged("pay-1", "PENDING", "APPROVED"));
    }

    @Test
    @DisplayName("publishStatusChanged returns without throwing on send failure")
    void publishStatusChanged_sendFails_noThrow() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        CompletableFuture<RecordMetadata> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("down"));
        when(producer.send(any(ProducerRecord.class), any())).thenReturn(failed);

        assertDoesNotThrow(() -> publisher.publishStatusChanged("pay-1", "PENDING", "APPROVED"));
    }
}
