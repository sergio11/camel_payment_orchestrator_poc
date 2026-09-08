package com.poc.camel.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class KafkaTestConsumer {

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private final List<ConsumerRecord<String, String>> buffer = new CopyOnWriteArrayList<>();

    public KafkaTestConsumer(String bootstrapServers, String groupId, String... topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Arrays.asList(topics));
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public <T> T consumeUntil(String topic, String key, Class<T> type, int timeoutSeconds) throws Exception {
        String json = consumeRaw(topic, key, timeoutSeconds);
        return json == null ? null : mapper.readValue(json, type);
    }

    public String consumeUntil(String topic, String key, int timeoutSeconds) throws Exception {
        return consumeRaw(topic, key, timeoutSeconds);
    }

    public String consumeUntilPredicate(String topic, Predicate<String> predicate, int timeoutSeconds) throws Exception {
        String hit = drainBuffer(topic, r -> predicate.test(r.value()));
        if (hit != null) {
            return hit;
        }
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            List<ConsumerRecord<String, String>> pending = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic()) && predicate.test(record.value())) {
                    buffer.addAll(pending);
                    return record.value();
                }
                pending.add(record);
            }
            buffer.addAll(pending);
        }
        return null;
    }

    public <T> T consumeAny(String topic, Class<T> type, int timeoutSeconds) throws Exception {
        String json = consumeAnyRaw(topic, timeoutSeconds);
        return json == null ? null : mapper.readValue(json, type);
    }

    private String consumeRaw(String topic, String key, int timeoutSeconds) {
        String hit = drainBuffer(topic, r -> key.equals(r.key()));
        if (hit != null) {
            return hit;
        }
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            List<ConsumerRecord<String, String>> pending = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic()) && key.equals(record.key())) {
                    buffer.addAll(pending);
                    return record.value();
                }
                pending.add(record);
            }
            buffer.addAll(pending);
        }
        return null;
    }

    private String consumeAnyRaw(String topic, int timeoutSeconds) {
        String hit = drainBuffer(topic, r -> true);
        if (hit != null) {
            return hit;
        }
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            List<ConsumerRecord<String, String>> pending = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic())) {
                    buffer.addAll(pending);
                    return record.value();
                }
                pending.add(record);
            }
            buffer.addAll(pending);
        }
        return null;
    }

    private String drainBuffer(String topic, Predicate<ConsumerRecord<String, String>> match) {
        for (ConsumerRecord<String, String> record : buffer) {
            if (topic.equals(record.topic()) && match.test(record)) {
                buffer.remove(record);
                return record.value();
            }
        }
        return null;
    }

    public void close() {
        if (consumer != null) {
            consumer.close();
        }
    }

    public static String randomGroupId() {
        return "test-" + UUID.randomUUID();
    }
}
