package com.poc.gateway.infrastructure.messaging.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import com.poc.shared.dto.PaymentMetadataDTO;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherAdapterTest {

    @Mock KafkaTopicConfig topicConfig;
    @Mock ObjectMapper objectMapper;
    @Mock PaymentEventSerializer serializer;
    @Mock KafkaProducer<String, String> producer;

    @InjectMocks KafkaEventPublisherAdapter adapter;

    private void setField(String name, Object value) throws Exception {
        Field field = KafkaEventPublisherAdapter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(adapter, value);
    }

    private void invokeInit() throws Exception {
        Method init = KafkaEventPublisherAdapter.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(adapter);
    }

    private RecordMetadata okRecordMetadata(String topic) {
        return new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0L, 0, 0);
    }

    @Test
    void init_createsProducerWithCorrectProperties() throws Exception {
        adapter.bootstrapServers = "localhost:9092";

        try (MockedConstruction<KafkaProducer> mocked = mockConstruction(KafkaProducer.class,
                (mock, context) -> {
                })) {

            invokeInit();

            assertEquals(1, mocked.constructed().size());

            Thread.sleep(100);
            adapter.close();
        }
    }

    @Test
    void init_withDifferentBootstrapServers() throws Exception {
        adapter.bootstrapServers = "broker1:9093,broker2:9094";

        try (MockedConstruction<KafkaProducer> mocked = mockConstruction(KafkaProducer.class,
                (mock, context) -> {
                })) {

            invokeInit();

            assertEquals(1, mocked.constructed().size());

            Thread.sleep(100);
            adapter.close();
        }
    }

    @Test
    void close_afterInit_closesProducer() throws Exception {
        adapter.bootstrapServers = "localhost:9092";

        try (MockedConstruction<KafkaProducer> mocked = mockConstruction(KafkaProducer.class,
                (mock, context) -> {
                })) {

            invokeInit();

            KafkaProducer<?, ?> constructedProducer = mocked.constructed().get(0);

            adapter.close();

            verify(constructedProducer).close();
        }
    }

    @Test
    void publishPaymentReceived_success_returnsTrue() throws Exception {
        setField("producer", producer);

        ObjectNode node = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(node);
        when(node.put(anyString(), anyString())).thenReturn(node);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":true}");
        when(topicConfig.received()).thenReturn("payments.received");
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(okRecordMetadata("payments.received")));

        boolean result = adapter.publishPaymentReceived(
            "pay-1", new BigDecimal("100"), "USD", "c1", "CARD", "US", null
        );

        assertTrue(result);
        verify(producer).send(any(ProducerRecord.class));
    }

    @Test
    void publishPaymentReceived_withMetadata_includesMetadata() throws Exception {
        setField("producer", producer);

        ObjectNode node = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(node);
        when(node.put(anyString(), anyString())).thenReturn(node);
        when(objectMapper.valueToTree(any())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":true}");
        when(topicConfig.received()).thenReturn("payments.received");
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(okRecordMetadata("payments.received")));

        PaymentMetadataDTO meta = new PaymentMetadataDTO("order-1", 3, true, 30, "LOW", null, null, null);
        boolean result = adapter.publishPaymentReceived(
            "pay-1", new BigDecimal("100"), "USD", "c1", "CARD", "US", meta
        );

        assertTrue(result);
    }

    @Test
    void publishPaymentReceived_nullAmount_usesZero() throws Exception {
        setField("producer", producer);

        ObjectNode node = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(node);
        when(node.put(anyString(), anyString())).thenReturn(node);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":true}");
        when(topicConfig.received()).thenReturn("payments.received");
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(okRecordMetadata("payments.received")));

        boolean result = adapter.publishPaymentReceived(
            "pay-1", null, "USD", "c1", "CARD", "US", null
        );

        assertTrue(result);
    }

    @Test
    void publishPaymentReceived_exception_returnsFalse() throws Exception {
        setField("producer", producer);

        ObjectNode node = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(node);
        when(node.put(anyString(), anyString())).thenReturn(node);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("kafka down"));

        boolean result = adapter.publishPaymentReceived(
            "pay-1", new BigDecimal("100"), "USD", "c1", "CARD", "US", null
        );

        assertFalse(result);
    }

    @Test
    void publishStatusChanged_success_returnsTrue() throws Exception {
        setField("producer", producer);

        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"status\":\"APPROVED\"}");
        when(topicConfig.statusChanged()).thenReturn("payments.status.changed");
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(okRecordMetadata("payments.status.changed")));

        boolean result = adapter.publishStatusChanged("pay-1", "APPROVED");

        assertTrue(result);
    }

    @Test
    void publishStatusChanged_exception_returnsFalse() throws Exception {
        setField("producer", producer);

        when(objectMapper.writeValueAsString(any(Map.class))).thenThrow(new RuntimeException("fail"));

        boolean result = adapter.publishStatusChanged("pay-1", "APPROVED");

        assertFalse(result);
    }

    @Test
    void publishDeadLetter_success_returnsTrue() throws Exception {
        setField("producer", producer);

        ObjectNode node = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(node);
        when(node.put(anyString(), anyString())).thenReturn(node);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"dlq\":true}");
        when(topicConfig.deadLetter()).thenReturn("payments.dlq");
        when(producer.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(okRecordMetadata("payments.dlq")));

        boolean result = adapter.publishDeadLetter("pay-1", "{\"original\":true}", "timeout");

        assertTrue(result);
    }

    @Test
    void publishDeadLetter_exception_returnsFalse() throws Exception {
        setField("producer", producer);

        ObjectNode node = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(node);
        when(node.put(anyString(), anyString())).thenReturn(node);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("fail"));

        boolean result = adapter.publishDeadLetter("pay-1", "payload", "reason");

        assertFalse(result);
    }

    @Test
    void close_withNonNullProducer_closes() throws Exception {
        setField("producer", producer);
        adapter.close();
        verify(producer).close();
    }

    @Test
    void close_withNullProducer_doesNotThrow() throws Exception {
        setField("producer", null);
        assertDoesNotThrow(() -> adapter.close());
    }
}
