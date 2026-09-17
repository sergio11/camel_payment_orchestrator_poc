package com.poc.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class KafkaConfigHelperTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String GROUP_ID = "test-group";

    @Test
    @DisplayName("producerConfig should set bootstrap.servers")
    void testProducerConfigBootstrapServers() {
        Properties props = KafkaConfigHelper.producerConfig(BOOTSTRAP_SERVERS);

        assertEquals(BOOTSTRAP_SERVERS, props.get("bootstrap.servers"));
    }

    @Test
    @DisplayName("producerConfig should set key and value serializers")
    void testProducerConfigSerializers() {
        Properties props = KafkaConfigHelper.producerConfig(BOOTSTRAP_SERVERS);

        assertEquals("org.apache.kafka.common.serialization.StringSerializer", props.get("key.serializer"));
        assertEquals("org.apache.kafka.common.serialization.StringSerializer", props.get("value.serializer"));
    }

    @Test
    @DisplayName("producerConfig should set acks=all")
    void testProducerConfigAcks() {
        Properties props = KafkaConfigHelper.producerConfig(BOOTSTRAP_SERVERS);

        assertEquals("all", props.get("acks"));
    }

    @Test
    @DisplayName("producerConfig should set retries, linger.ms, batch.size, buffer.memory, and delivery.timeout.ms")
    void testProducerConfigPerformanceSettings() {
        Properties props = KafkaConfigHelper.producerConfig(BOOTSTRAP_SERVERS);

        assertEquals(3, props.get("retries"));
        assertEquals(5, props.get("linger.ms"));
        assertEquals(16384, props.get("batch.size"));
        assertEquals(33554432L, props.get("buffer.memory"));
        assertEquals(5, props.get("max.in.flight.requests.per.connection"));
        assertEquals(120000, props.get("delivery.timeout.ms"));
    }

    @Test
    @DisplayName("consumerConfig should set bootstrap.servers and group.id")
    void testConsumerConfigBootstrapAndGroup() {
        Properties props = KafkaConfigHelper.consumerConfig(BOOTSTRAP_SERVERS, GROUP_ID);

        assertEquals(BOOTSTRAP_SERVERS, props.get("bootstrap.servers"));
        assertEquals(GROUP_ID, props.get("group.id"));
    }

    @Test
    @DisplayName("consumerConfig should set enable.auto.commit=false and auto.offset.reset=earliest")
    void testConsumerConfigCommitAndOffsetSettings() {
        Properties props = KafkaConfigHelper.consumerConfig(BOOTSTRAP_SERVERS, GROUP_ID);

        assertEquals(false, props.get("enable.auto.commit"));
        assertEquals("earliest", props.get("auto.offset.reset"));
    }

    @Test
    @DisplayName("consumerConfig should set key and value deserializers")
    void testConsumerConfigDeserializers() {
        Properties props = KafkaConfigHelper.consumerConfig(BOOTSTRAP_SERVERS, GROUP_ID);

        assertEquals("org.apache.kafka.common.serialization.StringDeserializer", props.get("key.deserializer"));
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer", props.get("value.deserializer"));
    }
}
