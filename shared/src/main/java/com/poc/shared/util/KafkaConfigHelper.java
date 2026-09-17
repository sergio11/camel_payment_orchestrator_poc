package com.poc.shared.util;

import java.util.Properties;

public final class KafkaConfigHelper {

    private KafkaConfigHelper() {}

    public static Properties producerConfig(String bootstrapServers) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("linger.ms", 5);
        props.put("batch.size", 16384);
        props.put("buffer.memory", 33554432L);
        props.put("max.in.flight.requests.per.connection", 5);
        props.put("delivery.timeout.ms", 120000);
        return props;
    }

    public static Properties consumerConfig(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", groupId);
        props.put("enable.auto.commit", false);
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }
}
