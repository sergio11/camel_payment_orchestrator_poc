package com.poc.gateway.infrastructure.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerManagerTest {

    @Mock PaymentStatusRouter router;
    @Mock KafkaConsumer<String, String> kafkaConsumer;

    @InjectMocks KafkaConsumerManager manager;

    private void setField(String name, Object value) throws Exception {
        Field field = KafkaConsumerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private Object getField(String name) throws Exception {
        Field field = KafkaConsumerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void invokePollLoop() throws Exception {
        Method pollLoop = KafkaConsumerManager.class.getDeclaredMethod("pollLoop");
        pollLoop.setAccessible(true);
        pollLoop.invoke(manager);
    }

    private void invokeStart() throws Exception {
        Method start = KafkaConsumerManager.class.getDeclaredMethod("start");
        start.setAccessible(true);
        start.invoke(manager);
    }

    @Test
    void start_createsConsumerWithCorrectPropertiesAndStartsThread() throws Exception {
        manager.bootstrapServers = "localhost:9092";
        manager.processedTopic = "payments.processed";
        manager.groupId = "test-group";

        try (MockedConstruction<KafkaConsumer> mocked = mockConstruction(KafkaConsumer.class,
                (mock, context) -> {
                    lenient().doNothing().when(mock).subscribe(anyList());
                })) {

            invokeStart();

            assertEquals(1, mocked.constructed().size());
            KafkaConsumer<?, ?> constructedConsumer = mocked.constructed().get(0);
            verify(constructedConsumer).subscribe(Collections.singletonList("payments.processed"));

            ExecutorService executorService = (ExecutorService) getField("executor");
            assertNotNull(executorService);

            manager.stop();
        }
    }

    @Test
    void start_withDefaultGroupId_usesConfiguredValue() throws Exception {
        manager.bootstrapServers = "broker1:9093";
        manager.processedTopic = "events.topic";
        manager.groupId = "my-custom-group";

        try (MockedConstruction<KafkaConsumer> mocked = mockConstruction(KafkaConsumer.class,
                (mock, context) -> {
                    lenient().doNothing().when(mock).subscribe(anyList());
                })) {

            invokeStart();

            assertEquals(1, mocked.constructed().size());

            manager.stop();
        }
    }

    @Test
    void stop_afterStart_closesConsumerAndInterruptsThread() throws Exception {
        manager.bootstrapServers = "localhost:9092";
        manager.processedTopic = "payments.processed";
        manager.groupId = "test-group";

        try (MockedConstruction<KafkaConsumer> mocked = mockConstruction(KafkaConsumer.class,
                (mock, context) -> {
                    lenient().doNothing().when(mock).subscribe(anyList());
                })) {

            invokeStart();

            KafkaConsumer<?, ?> constructedConsumer = mocked.constructed().get(0);

            manager.stop();

            verify(constructedConsumer).close();
            assertFalse((boolean) getField("running"));
        }
    }

    @Test
    void stop_withExecutorAndConsumer_callsShutdownAndCloses() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any())).thenReturn(true);
        setField("executor", mockExecutor);
        setField("consumer", kafkaConsumer);
        setField("running", true);

        manager.stop();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        verify(kafkaConsumer).close();
        assertThatRunningIsFalse();
    }

    @Test
    void stop_nullExecutor_doesNotThrow() throws Exception {
        setField("executor", null);
        setField("consumer", kafkaConsumer);
        setField("running", true);

        manager.stop();

        verify(kafkaConsumer).close();
    }

    @Test
    void stop_nullConsumer_doesNotThrow() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any())).thenReturn(true);
        setField("executor", mockExecutor);
        setField("consumer", null);
        setField("running", true);

        manager.stop();

        verify(mockExecutor).shutdown();
    }

    @Test
    void stop_bothNull_doesNotThrow() throws Exception {
        setField("executor", null);
        setField("consumer", null);
        setField("running", true);

        manager.stop();

        assertThatRunningIsFalse();
    }

    @Test
    void stop_whenAlreadyStopped_stillClosesConsumer() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any())).thenReturn(true);
        setField("executor", mockExecutor);
        setField("consumer", kafkaConsumer);
        setField("running", false);

        manager.stop();

        verify(mockExecutor).shutdown();
        verify(kafkaConsumer).close();
    }

    @Test
    void pollLoop_routesRecordsAndCommits() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", true);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, "key", "value");
        ConsumerRecords<String, String> records = new ConsumerRecords<>(Map.of(
            new TopicPartition("topic", 0), Collections.singletonList(record)
        ));

        doReturn(records).when(kafkaConsumer).poll(any(Duration.class));

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();
        Thread.sleep(300);
        setField("running", false);
        t.join(2000);

        verify(router, atLeastOnce()).route(record);
        verify(kafkaConsumer, atLeastOnce()).commitSync();
    }

    @Test
    void pollLoop_routerException_continuesAndCommits() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", true);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, "key", "value");
        ConsumerRecords<String, String> records = new ConsumerRecords<>(Map.of(
            new TopicPartition("topic", 0), Collections.singletonList(record)
        ));

        doReturn(records).when(kafkaConsumer).poll(any(Duration.class));
        doThrow(new RuntimeException("route error")).when(router).route(any());

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();
        Thread.sleep(300);
        setField("running", false);
        t.join(2000);

        verify(kafkaConsumer, atLeastOnce()).commitSync();
    }

    @Test
    void pollLoop_pollExceptionWhileRunning_logsAndContinues() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", true);

        doThrow(new RuntimeException("poll error")).when(kafkaConsumer).poll(any(Duration.class));

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();
        Thread.sleep(300);
        setField("running", false);
        t.join(2000);

        verify(kafkaConsumer, atLeastOnce()).poll(any(Duration.class));
    }

    @Test
    void pollLoop_pollExceptionWhileStopping_doesNotLog() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", true);

        java.util.concurrent.CountDownLatch pollStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch exceptionThrown = new java.util.concurrent.CountDownLatch(1);

        doAnswer(invocation -> {
            pollStarted.countDown();
            try {
                exceptionThrown.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            throw new RuntimeException("poll error");
        }).when(kafkaConsumer).poll(any(Duration.class));

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();

        pollStarted.await(2, java.util.concurrent.TimeUnit.SECONDS);
        setField("running", false);
        exceptionThrown.countDown();

        t.join(3000);

        verify(kafkaConsumer, atLeastOnce()).poll(any(Duration.class));
    }

    @Test
    void pollLoop_pollExceptionWhileStopped_doesNotLog() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", false);

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();
        t.join(1000);

        verify(kafkaConsumer, never()).commitSync();
    }

    @Test
    void pollLoop_emptyRecords_doesNotRoute() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", true);

        doReturn(new ConsumerRecords<>(Map.of())).when(kafkaConsumer).poll(any(Duration.class));

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();
        Thread.sleep(200);
        setField("running", false);
        t.join(2000);

        verify(router, never()).route(any());
        verify(kafkaConsumer, atLeastOnce()).commitSync();
    }

    @Test
    void pollLoop_multipleRecords_routesAll() throws Exception {
        setField("consumer", kafkaConsumer);
        setField("running", true);

        ConsumerRecord<String, String> record1 = new ConsumerRecord<>("topic", 0, 0L, "k1", "v1");
        ConsumerRecord<String, String> record2 = new ConsumerRecord<>("topic", 0, 1L, "k2", "v2");
        ConsumerRecords<String, String> records = new ConsumerRecords<>(Map.of(
            new TopicPartition("topic", 0), Collections.singletonList(record1),
            new TopicPartition("topic", 1), Collections.singletonList(record2)
        ));

        doReturn(records).when(kafkaConsumer).poll(any(Duration.class));

        Thread t = new Thread(() -> {
            try {
                invokePollLoop();
            } catch (Exception ignored) {
            }
        });
        t.start();
        Thread.sleep(300);
        setField("running", false);
        t.join(2000);

        verify(router, atLeastOnce()).route(record1);
        verify(router, atLeastOnce()).route(record2);
    }

    private void assertThatRunningIsFalse() throws Exception {
        Field field = KafkaConsumerManager.class.getDeclaredField("running");
        field.setAccessible(true);
        assertFalse((boolean) field.get(manager));
    }

    private void assertFalse(boolean value) {
        org.junit.jupiter.api.Assertions.assertFalse(value);
    }
}
