package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class KafkaPaymentStatusConsumerBranchesTest {

    @Mock
    PaymentRepository repository;

    @Mock
    KafkaEventPublisher kafkaEventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    Instance<MeterRegistry> meterRegistries;

    @Mock
    MeterRegistry meterRegistry;

    @Mock
    Counter counter;

    private KafkaPaymentStatusConsumer consumer;
    private Payment testPayment;

    @BeforeEach
    void setUp() throws Exception {
        consumer = new KafkaPaymentStatusConsumer();
        setField("repository", repository);
        setField("kafkaEventPublisher", kafkaEventPublisher);
        setField("objectMapper", objectMapper);
        setField("meterRegistries", meterRegistries);
        consumer.bootstrapServers = "localhost:9092";
        consumer.paymentsProcessedTopic = "payments.processed";
        consumer.paymentsFailedTopic = "payments.failed";
        consumer.paymentsReviewTopic = "payments.review";

        testPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING, null, null,
            Map.of(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void setField(String name, Object value) throws Exception {
        Field f = KafkaPaymentStatusConsumer.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(consumer, value);
    }

    private Object invokeProcessRecord(ConsumerRecord<String, String> record) throws Exception {
        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("processRecord", ConsumerRecord.class);
        m.setAccessible(true);
        try {
            return m.invoke(consumer, record);
        } catch (InvocationTargetException e) {
            throw e;
        }
    }

    private ConsumerRecord<String, String> record(String topic, String key) {
        return new ConsumerRecord<>(topic, 0, 0L, key, "{}");
    }

    private void expectTransient(ConsumerRecord<String, String> record, String fragment) throws Exception {
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
            () -> invokeProcessRecord(record));
        assertInstanceOf(KafkaPaymentStatusConsumer.TransientConsumerException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains(fragment));
    }

    // ========== processRecord transient / skip paths ==========

    @Test
    @DisplayName("processRecord throws transient when DB lookup fails")
    void processRecord_dbLookupFails_transient() throws Exception {
        UUID id = testPayment.id();
        when(repository.findById(id)).thenThrow(new RuntimeException("db down"));
        expectTransient(record("payments.processed", id.toString()), "DB lookup failed");
    }

    @Test
    @DisplayName("processRecord throws transient when DB update fails")
    void processRecord_dbUpdateFails_transient() throws Exception {
        UUID id = testPayment.id();
        when(repository.findById(id)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(id, PaymentStatus.APPROVED)).thenThrow(new RuntimeException("db down"));
        expectTransient(record("payments.processed", id.toString()), "DB update failed");
    }

    @Test
    @DisplayName("processRecord skips already-transitioned payments")
    void processRecord_alreadyTransitioned_skips() throws Exception {
        UUID id = testPayment.id();
        when(repository.findById(id)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(id, PaymentStatus.APPROVED)).thenReturn(Optional.empty());
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);

        invokeProcessRecord(record("payments.processed", id.toString()));

        verify(kafkaEventPublisher, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("processRecord throws transient when publish returns false")
    void processRecord_publishFalse_transient() throws Exception {
        UUID id = testPayment.id();
        when(repository.findById(id)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(id, PaymentStatus.APPROVED))
            .thenReturn(Optional.of(testPayment.withStatus(PaymentStatus.APPROVED)));
        when(kafkaEventPublisher.publishStatusChanged(anyString(), anyString(), anyString())).thenReturn(false);

        expectTransient(record("payments.processed", id.toString()), "returned false");
    }

    @Test
    @DisplayName("processRecord throws transient when publish throws")
    void processRecord_publishThrows_transient() throws Exception {
        UUID id = testPayment.id();
        when(repository.findById(id)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(id, PaymentStatus.APPROVED))
            .thenReturn(Optional.of(testPayment.withStatus(PaymentStatus.APPROVED)));
        doThrow(new RuntimeException("kafka down")).when(kafkaEventPublisher)
            .publishStatusChanged(anyString(), anyString(), anyString());

        expectTransient(record("payments.processed", id.toString()), "publish failed");
    }

    // ========== routePoisonToDlq branches ==========

    private void invokePoison(ConsumerRecord<String, String> record, String reason) throws Exception {
        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("routePoisonToDlq",
            ConsumerRecord.class, String.class);
        m.setAccessible(true);
        m.invoke(consumer, record, reason);
    }

    @Test
    @DisplayName("routePoisonToDlq succeeds when DLQ publish works")
    void routePoison_dlqOk_noThrow() throws Exception {
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);
        when(kafkaEventPublisher.publishDeadLetter(any(), any(), any(), any())).thenReturn(true);

        invokePoison(record("payments.processed", "k"), "boom");

        verify(kafkaEventPublisher).publishDeadLetter(eq("k"), eq("payments.processed"), eq("boom"), eq("{}"));
    }

    @Test
    @DisplayName("routePoisonToDlq tolerates DLQ publish throwing")
    void routePoison_dlqThrows_tolerated() throws Exception {
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);
        doThrow(new RuntimeException("dlq down")).when(kafkaEventPublisher)
            .publishDeadLetter(any(), any(), any(), any());

        assertDoesNotThrow(() -> invokePoison(record("payments.processed", "k"), "boom"));
    }

    @Test
    @DisplayName("routePoisonToDlq increments dlqFailed counter when DLQ publish returns false")
    void routePoison_dlqReturnsFalse_incrementsDlqFailed() throws Exception {
        when(meterRegistries.isUnsatisfied()).thenReturn(false);
        when(meterRegistries.get()).thenReturn(meterRegistry);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        when(kafkaEventPublisher.publishDeadLetter(any(), any(), any(), any())).thenReturn(false);

        invokePoison(record("payments.processed", "k"), "boom");

        verify(kafkaEventPublisher).publishDeadLetter(eq("k"), eq("payments.processed"), eq("boom"), eq("{}"));
        verify(counter, times(2)).increment();
    }

    // ========== incrementCounter branches ==========

    private void invokeCounter(String name, String topic) throws Exception {
        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("incrementCounter", String.class, String.class);
        m.setAccessible(true);
        m.invoke(consumer, name, topic);
    }

    @Test
    @DisplayName("incrementCounter uses registry when available")
    void incrementCounter_registryAvailable_increments() throws Exception {
        when(meterRegistries.isUnsatisfied()).thenReturn(false);
        when(meterRegistries.get()).thenReturn(meterRegistry);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        invokeCounter("x", "payments.processed");

        verify(counter).increment();
    }

    @Test
    @DisplayName("incrementCounter tolerates registry failure")
    void incrementCounter_registryThrows_tolerated() throws Exception {
        when(meterRegistries.isUnsatisfied()).thenReturn(false);
        when(meterRegistries.get()).thenThrow(new RuntimeException("no registry"));

        assertDoesNotThrow(() -> invokeCounter("x", "t"));
    }

    @Test
    @DisplayName("incrementCounter skips when unsatisfied")
    void incrementCounter_unsatisfied_noOp() throws Exception {
        when(meterRegistries.isUnsatisfied()).thenReturn(true);

        assertDoesNotThrow(() -> invokeCounter("x", "t"));
        verify(meterRegistries, never()).get();
    }

    // ========== consumeLoop via mocked KafkaConsumer ==========

    private ConsumerRecords<String, String> recordsOf(ConsumerRecord<String, String>... records) {
        if (records.length == 0) {
            return ConsumerRecords.empty();
        }
        return new ConsumerRecords<>(Map.of(new TopicPartition("payments.processed", 0), List.of(records)));
    }

    private void invokeConsumeLoop() throws Exception {
        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("consumeLoop");
        m.setAccessible(true);
        m.invoke(consumer);
    }

    private void stubHappyPayment(UUID id) {
        when(repository.findById(id)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(eq(id), any())).thenAnswer(inv -> {
            PaymentStatus s = inv.getArgument(1);
            return Optional.of(testPayment.withStatus(s));
        });
        when(kafkaEventPublisher.publishStatusChanged(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private Thread stopAfter(long millis) {
        Thread stopper = new Thread(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumer.stop();
        });
        stopper.setDaemon(true);
        stopper.start();
        return stopper;
    }

    @Test
    @DisplayName("consumeLoop processes records and commits offsets")
    void consumeLoop_happy_commits() throws Exception {
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);
        UUID id = testPayment.id();
        stubHappyPayment(id);
        lenient().when(kafkaEventPublisher.publishDeadLetter(any(), any(), any(), any())).thenReturn(false);

        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class,
                (mock, ctx) -> {
                    when(mock.poll(any(Duration.class)))
                        .thenReturn(recordsOf(record("payments.processed", id.toString())))
                        .thenReturn(ConsumerRecords.empty());
                })) {
            Thread stopper = stopAfter(800);
            invokeConsumeLoop();
            stopper.join(5000);

            KafkaConsumer mock = mc.constructed().get(0);
            verify(mock).subscribe(anyList());
            verify(mock, atLeastOnce()).poll(any(Duration.class));
            verify(mock).commitSync(anyMap());
            verify(mock).close();
        }
    }

    @Test
    @DisplayName("consumeLoop breaks on transient error without committing poison offset")
    void consumeLoop_transient_breaks() throws Exception {
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);
        UUID goodId = testPayment.id();
        UUID badId = UUID.randomUUID();
        Payment other = new Payment(badId, new BigDecimal("5.00"), "USD", "c", "CARD",
            "US", PaymentStatus.PENDING, null, null, Map.of(), LocalDateTime.now(), LocalDateTime.now());
        when(repository.findById(goodId)).thenReturn(Optional.of(testPayment));
        when(repository.updateIfPending(eq(goodId), any())).thenReturn(Optional.of(testPayment));
        when(repository.findById(badId)).thenReturn(Optional.of(other));
        when(repository.updateIfPending(eq(badId), any())).thenReturn(Optional.of(other));
        when(kafkaEventPublisher.publishStatusChanged(eq(goodId.toString()), anyString(), anyString())).thenReturn(true);
        when(kafkaEventPublisher.publishStatusChanged(eq(badId.toString()), anyString(), anyString())).thenReturn(false);
        lenient().when(kafkaEventPublisher.publishDeadLetter(any(), any(), any(), any())).thenReturn(false);

        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class,
                (mock, ctx) -> {
                    when(mock.poll(any(Duration.class)))
                        .thenReturn(recordsOf(
                            record("payments.processed", goodId.toString()),
                            record("payments.processed", badId.toString())))
                        .thenReturn(ConsumerRecords.empty());
                })) {
            Thread stopper = stopAfter(800);
            invokeConsumeLoop();
            stopper.join(5000);

            KafkaConsumer mock = mc.constructed().get(0);
            verify(mock).commitSync((Map) argThat((Map m) -> m.size() == 1));
            verify(mock).close();
        }
    }

    @Test
    @DisplayName("consumeLoop routes unexpected failures to DLQ and commits")
    void consumeLoop_unexpectedFailure_dlqAndCommits() throws Exception {
        UUID id = testPayment.id();
        when(repository.findById(id)).thenReturn(null);
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);
        lenient().when(kafkaEventPublisher.publishDeadLetter(any(), any(), any(), any())).thenReturn(false);

        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class,
                (mock, ctx) -> {
                    when(mock.poll(any(Duration.class)))
                        .thenReturn(recordsOf(record("payments.processed", id.toString())))
                        .thenReturn(ConsumerRecords.empty());
                })) {
            Thread stopper = stopAfter(800);
            invokeConsumeLoop();
            stopper.join(5000);

            KafkaConsumer mock = mc.constructed().get(0);
            verify(mock).commitSync(anyMap());
            verify(kafkaEventPublisher).publishDeadLetter(eq(id.toString()), anyString(), any(), any());
            verify(mock).close();
        }
    }

    @Test
    @DisplayName("consumeLoop routes poison records and commits to avoid loops")
    void consumeLoop_poison_commits() throws Exception {
        lenient().when(meterRegistries.isUnsatisfied()).thenReturn(true);
        lenient().when(kafkaEventPublisher.publishDeadLetter(any(), any(), any(), any())).thenReturn(false);

        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class,
                (mock, ctx) -> {
                    when(mock.poll(any(Duration.class)))
                        .thenReturn(recordsOf(record("payments.processed", "not-a-uuid")))
                        .thenReturn(ConsumerRecords.empty());
                })) {
            Thread stopper = stopAfter(800);
            invokeConsumeLoop();
            stopper.join(5000);

            KafkaConsumer mock = mc.constructed().get(0);
            verify(mock).commitSync(anyMap());
            verify(kafkaEventPublisher).publishDeadLetter(eq("not-a-uuid"), anyString(), anyString(), any());
            verify(mock).close();
        }
    }

    @Test
    @DisplayName("consumeLoop breaks on wakeup")
    void consumeLoop_wakeup_breaks() throws Exception {
        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class,
                (mock, ctx) -> {
                    when(mock.poll(any(Duration.class)))
                        .thenThrow(new org.apache.kafka.common.errors.WakeupException());
                })) {
            invokeConsumeLoop();

            KafkaConsumer mock = mc.constructed().get(0);
            verify(mock).close();
            verify(mock, never()).commitSync(anyMap());
        }
    }

    @Test
    @DisplayName("consumeLoop retries after generic error and honors interrupt")
    void consumeLoop_genericError_interrupt_breaks() throws Exception {
        Thread testThread = Thread.currentThread();
        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class,
                (mock, ctx) -> {
                    when(mock.poll(any(Duration.class))).thenThrow(new RuntimeException("boom"));
                })) {
            Thread interrupter = new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                testThread.interrupt();
            });
            interrupter.setDaemon(true);
            interrupter.start();

            invokeConsumeLoop();
            Thread.interrupted();
            interrupter.join(5000);

            KafkaConsumer mock = mc.constructed().get(0);
            verify(mock).close();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("stop() with null consumer does not throw")
    void stop_nullConsumer_noThrow() throws Exception {
        setField("consumer", null);
        assertDoesNotThrow(() -> consumer.stop());
        Field rf = KafkaPaymentStatusConsumer.class.getDeclaredField("running");
        rf.setAccessible(true);
        assertFalse(((AtomicBoolean) rf.get(consumer)).get());
    }

    @Test
    @DisplayName("stop() wakes up a set consumer")
    void stop_setConsumer_wakesUp() throws Exception {
        KafkaConsumer<String, String> mock = mock(KafkaConsumer.class);
        setField("consumer", mock);
        consumer.stop();
        verify(mock).wakeup();
    }

    @Test
    @DisplayName("crashed thread exits without restart when already stopped")
    void startConsumerThread_stoppedOnCrash_exits() throws Exception {
        consumer.bootstrapServers = null;
        consumer.stop();

        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("startConsumerThread");
        m.setAccessible(true);
        m.invoke(consumer);

        Field tf = KafkaPaymentStatusConsumer.class.getDeclaredField("consumerThread");
        tf.setAccessible(true);
        Thread consumerThread = (Thread) tf.get(consumer);
        assertNotNull(consumerThread);
        consumerThread.join(5000);
        assertFalse(consumerThread.isAlive());
    }

    @Test
    @DisplayName("stop racing with crash still terminates the thread")
    void startConsumerThread_stopRacesCrash_terminates() throws Exception {
        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("startConsumerThread");
        m.setAccessible(true);
        Field tf = KafkaPaymentStatusConsumer.class.getDeclaredField("consumerThread");
        tf.setAccessible(true);
        Field rf = KafkaPaymentStatusConsumer.class.getDeclaredField("running");
        rf.setAccessible(true);
        Field cf = KafkaPaymentStatusConsumer.class.getDeclaredField("consumer");
        cf.setAccessible(true);

        for (int i = 0; i < 100; i++) {
            KafkaPaymentStatusConsumer c = new KafkaPaymentStatusConsumer();
            c.bootstrapServers = null;
            m.invoke(c);
            c.stop();
            Thread t = (Thread) tf.get(c);
            t.join(2000);
            if (t.isAlive()) {
                t.interrupt();
                t.join(2000);
            }
            assertFalse(t.isAlive());
            cf.set(c, null);
            ((AtomicBoolean) rf.get(c)).set(false);
        }
    }

    @Test
    @DisplayName("consumer thread restarts after unexpected death")
    void startConsumerThread_dies_restarts() throws Exception {
        consumer.bootstrapServers = null;

        var m = KafkaPaymentStatusConsumer.class.getDeclaredMethod("startConsumerThread");
        m.setAccessible(true);
        m.invoke(consumer);

        Field tf = KafkaPaymentStatusConsumer.class.getDeclaredField("consumerThread");
        tf.setAccessible(true);
        Thread consumerThread = (Thread) tf.get(consumer);
        assertNotNull(consumerThread);

        long deadline = System.currentTimeMillis() + 8000;
        while (consumerThread.getState() != Thread.State.TIMED_WAITING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(Thread.State.TIMED_WAITING, consumerThread.getState());
        Field rf = KafkaPaymentStatusConsumer.class.getDeclaredField("running");
        rf.setAccessible(true);
        assertTrue(((AtomicBoolean) rf.get(consumer)).get());

        consumerThread.interrupt();
        consumerThread.join(5000);
        assertFalse(consumerThread.isAlive());

        Field cf = KafkaPaymentStatusConsumer.class.getDeclaredField("consumer");
        cf.setAccessible(true);
        cf.set(consumer, null);
        ((AtomicBoolean) rf.get(consumer)).set(false);
    }
}
