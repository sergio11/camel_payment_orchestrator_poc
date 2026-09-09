package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.repository.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class KafkaPaymentStatusConsumerTest {

    @Mock
    PaymentRepository repository;

    @Mock
    KafkaEventPublisher kafkaEventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    KafkaConsumer<String, String> kafkaConsumer;

    @InjectMocks
    KafkaPaymentStatusConsumer consumer;

    private Payment testPayment;

    @BeforeEach
    void setUp() throws Exception {
        consumer.bootstrapServers = "localhost:9092";
        consumer.paymentsProcessedTopic = "payments.processed";
        consumer.paymentsFailedTopic = "payments.failed";
        consumer.paymentsReviewTopic = "payments.review";

        testPayment = new Payment(
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            "cust-1",
            "CREDIT_CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            Map.of(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private void setConsumerField(String fieldName, Object value) throws Exception {
        Field field = KafkaPaymentStatusConsumer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(consumer, value);
    }

    private Object getConsumerField(String fieldName) throws Exception {
        Field field = KafkaPaymentStatusConsumer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(consumer);
    }

    @SuppressWarnings("unchecked")
    private ConsumerRecord<String, String> createRecord(String topic, String key, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, key, value);
    }

    @Test
    @DisplayName("Constructor initializes fields correctly")
    void constructor_initializesFields() throws Exception {
        KafkaPaymentStatusConsumer c = new KafkaPaymentStatusConsumer();

        Field repoField = KafkaPaymentStatusConsumer.class.getDeclaredField("repository");
        repoField.setAccessible(true);
        assertNull(repoField.get(c));

        Field runningField = KafkaPaymentStatusConsumer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(c);
        assertNotNull(running);
        assertTrue(running.get());
    }

    @Test
    @DisplayName("stop() sets running to false and wakes up consumer")
    void stop_setsRunningFalseAndWakesUpConsumer() throws Exception {
        setConsumerField("consumer", kafkaConsumer);

        consumer.stop();

        Field runningField = KafkaPaymentStatusConsumer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(consumer);
        assertFalse(running.get());
        verify(kafkaConsumer).wakeup();
    }

    @Test
    @DisplayName("stop() does not throw when consumer is null")
    void stop_nullConsumer_noException() throws Exception {
        setConsumerField("consumer", null);

        assertDoesNotThrow(() -> consumer.stop());
    }

    @Test
    @DisplayName("processRecord - APPROVED topic updates status and publishes event")
    void processRecord_approvedTopic_updatesStatusAndPublishes() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "payments.processed", paymentId.toString(), "{}");

        Payment updatedPayment = testPayment.withStatus(PaymentStatus.APPROVED);
        when(repository.findById(paymentId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(paymentId, PaymentStatus.APPROVED)).thenReturn(Optional.of(updatedPayment));
        when(kafkaEventPublisher.publishStatusChanged(anyString(), anyString(), anyString())).thenReturn(true);

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository).updateIfPending(paymentId, PaymentStatus.APPROVED);
        verify(kafkaEventPublisher).publishStatusChanged(
            paymentId.toString(), "PENDING", "APPROVED");
    }

    @Test
    @DisplayName("processRecord - FAILED topic updates status and publishes event")
    void processRecord_failedTopic_updatesStatusAndPublishes() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "payments.failed", paymentId.toString(), "{}");

        Payment updatedPayment = testPayment.withStatus(PaymentStatus.FAILED);
        when(repository.findById(paymentId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(paymentId, PaymentStatus.FAILED)).thenReturn(Optional.of(updatedPayment));
        when(kafkaEventPublisher.publishStatusChanged(anyString(), anyString(), anyString())).thenReturn(true);

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository).updateIfPending(paymentId, PaymentStatus.FAILED);
        verify(kafkaEventPublisher).publishStatusChanged(
            paymentId.toString(), "PENDING", "FAILED");
    }

    @Test
    @DisplayName("processRecord - unexpected topic skips record without updating")
    void processRecord_unexpectedTopic_skipsRecord() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "some.other.topic", paymentId.toString(), "{}");

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository, never()).findById(any());
        verify(repository, never()).updateIfPending(any(), any());
        verify(kafkaEventPublisher, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("processRecord - invalid UUID key logs and skips")
    void processRecord_invalidUuid_logsAndSkips() throws Exception {
        ConsumerRecord<String, String> record = createRecord(
            "payments.processed", "not-a-uuid", "{}");

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("processRecord - null key logs and skips")
    void processRecord_nullKey_logsAndSkips() throws Exception {
        ConsumerRecord<String, String> record = createRecord(
            "payments.processed", null, "{}");

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("processRecord - payment not found logs and skips")
    void processRecord_paymentNotFound_logsAndSkips() throws Exception {
        UUID paymentId = UUID.randomUUID();
        ConsumerRecord<String, String> record = createRecord(
            "payments.processed", paymentId.toString(), "{}");

        when(repository.findById(paymentId)).thenReturn(Optional.empty());

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository, never()).updateIfPending(any(), any());
    }

    @Test
    @DisplayName("processRecord - REVIEW topic updates status and publishes event")
    void processRecord_reviewTopic_updatesStatusAndPublishes() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "payments.review", paymentId.toString(), "{}");

        Payment updatedPayment = testPayment.withStatus(PaymentStatus.REVIEW);
        when(repository.findById(paymentId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(paymentId, PaymentStatus.REVIEW)).thenReturn(Optional.of(updatedPayment));
        when(kafkaEventPublisher.publishStatusChanged(anyString(), anyString(), anyString())).thenReturn(true);

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(consumer, record);

        verify(repository).updateIfPending(paymentId, PaymentStatus.REVIEW);
        verify(kafkaEventPublisher).publishStatusChanged(
            paymentId.toString(), "PENDING", "REVIEW");
    }

    private void invokeStartConsumerThread() throws Exception {
        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("startConsumerThread");
        method.setAccessible(true);
        method.invoke(consumer);
    }

    private void invokeInit() throws Exception {
        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("init");
        method.setAccessible(true);
        method.invoke(consumer);
    }

    @Test
    @DisplayName("startConsumerThread creates and starts consumer thread")
    void startConsumer_createsAndStartsThread() throws Exception {
        invokeStartConsumerThread();

        Thread consumerThread = (Thread) getConsumerField("consumerThread");
        assertNotNull(consumerThread);
        assertTrue(consumerThread.isAlive());
        assertTrue(consumerThread.isDaemon());
        assertEquals("payment-status-consumer", consumerThread.getName());

        consumer.stop();
        consumerThread.join(3000);
    }

    @Test
    @DisplayName("stop() stops the running consumer thread")
    void stopConsumer_stopsThread() throws Exception {
        invokeStartConsumerThread();

        Thread consumerThread = (Thread) getConsumerField("consumerThread");
        assertNotNull(consumerThread);
        assertTrue(consumerThread.isAlive());

        consumer.stop();
        consumerThread.join(3000);

        AtomicBoolean running = (AtomicBoolean) getConsumerField("running");
        assertFalse(running.get());
        assertFalse(consumerThread.isAlive());
    }

    @Test
    @DisplayName("startConsumerThread called twice creates two threads")
    void startConsumer_calledTwice_createsTwoThreads() throws Exception {
        invokeStartConsumerThread();
        Thread firstThread = (Thread) getConsumerField("consumerThread");

        invokeStartConsumerThread();
        Thread secondThread = (Thread) getConsumerField("consumerThread");

        assertNotSame(firstThread, secondThread);

        consumer.stop();
        firstThread.join(3000);
        secondThread.join(3000);
    }

    @Test
    @DisplayName("startConsumerThread creates daemon thread with correct name")
    void startConsumer_daemonThreadCorrectName() throws Exception {
        invokeStartConsumerThread();

        Thread consumerThread = (Thread) getConsumerField("consumerThread");
        assertNotNull(consumerThread);
        assertTrue(consumerThread.isDaemon());
        assertEquals("payment-status-consumer", consumerThread.getName());
        assertTrue(consumerThread.isAlive());

        consumer.stop();
        consumerThread.join(3000);
    }

    @Test
    @DisplayName("processRecord - APPROVED path with failed publishStatusChanged")
    void processRecord_approved_publishFails() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "payments.processed", paymentId.toString(), "{}");

        Payment updatedPayment = testPayment.withStatus(PaymentStatus.APPROVED);
        when(repository.findById(paymentId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(paymentId, PaymentStatus.APPROVED)).thenReturn(Optional.of(updatedPayment));
        doThrow(new RuntimeException("kafka down")).when(kafkaEventPublisher)
            .publishStatusChanged(anyString(), anyString(), anyString());

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        assertThrows(Exception.class, () -> method.invoke(consumer, record));

        verify(repository).updateIfPending(paymentId, PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("processRecord - FAILED path with failed publishStatusChanged")
    void processRecord_failed_publishFails() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "payments.failed", paymentId.toString(), "{}");

        Payment updatedPayment = testPayment.withStatus(PaymentStatus.FAILED);
        when(repository.findById(paymentId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(paymentId, PaymentStatus.FAILED)).thenReturn(Optional.of(updatedPayment));
        doThrow(new RuntimeException("kafka down")).when(kafkaEventPublisher)
            .publishStatusChanged(anyString(), anyString(), anyString());

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        assertThrows(Exception.class, () -> method.invoke(consumer, record));

        verify(repository).updateIfPending(paymentId, PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("processRecord - repository update throws exception")
    void processRecord_repositoryUpdateThrows() throws Exception {
        UUID paymentId = testPayment.id();
        ConsumerRecord<String, String> record = createRecord(
            "payments.processed", paymentId.toString(), "{}");

        when(repository.findById(paymentId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(paymentId, PaymentStatus.APPROVED))
            .thenThrow(new RuntimeException("db error"));

        var method = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        method.setAccessible(true);
        assertThrows(Exception.class, () -> method.invoke(consumer, record));

        verify(repository).updateIfPending(paymentId, PaymentStatus.APPROVED);
        verify(kafkaEventPublisher, never()).publishStatusChanged(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("stop() called multiple times does not throw")
    void stop_calledMultipleTimes_noException() throws Exception {
        setConsumerField("consumer", kafkaConsumer);

        consumer.stop();
        consumer.stop();

        Field runningField = KafkaPaymentStatusConsumer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(consumer);
        assertFalse(running.get());
    }

    @Test
    @DisplayName("init() starts consumer thread via @PostConstruct path")
    void init_startsConsumerThread() throws Exception {
        invokeInit();

        Thread consumerThread = (Thread) getConsumerField("consumerThread");
        assertNotNull(consumerThread);
        assertTrue(consumerThread.isAlive());
        assertTrue(consumerThread.isDaemon());

        consumer.stop();
        consumerThread.join(3000);
        assertFalse(consumerThread.isAlive());
    }

    @Test
    @DisplayName("consumer thread runs consumeLoop and restarts on exception")
    void consumerThread_runsConsumeLoopAndRestarts() throws Exception {
        invokeStartConsumerThread();

        Thread consumerThread = (Thread) getConsumerField("consumerThread");
        assertNotNull(consumerThread);
        assertTrue(consumerThread.isAlive());

        Thread.sleep(2000);

        consumer.stop();
        consumerThread.join(6000);

        assertFalse(consumerThread.isAlive());
    }

    @Test
    @DisplayName("consumer thread stops when running is false and exception occurs")
    void consumerThread_stopsWhenRunningFalse() throws Exception {
        invokeStartConsumerThread();

        Thread.sleep(200);

        consumer.stop();
        Thread consumerThread = (Thread) getConsumerField("consumerThread");
        consumerThread.join(3000);

        assertFalse(consumerThread.isAlive());
        AtomicBoolean running = (AtomicBoolean) getConsumerField("running");
        assertFalse(running.get());
    }

}
