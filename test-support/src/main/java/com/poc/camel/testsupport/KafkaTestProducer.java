package com.poc.camel.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class KafkaTestProducer {

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;

    public KafkaTestProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        this.producer = new KafkaProducer<>(props);
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public void send(String topic, String key, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            producer.send(new ProducerRecord<>(topic, key, json)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to topic " + topic, e);
        }
    }

    public void sendRaw(String topic, String key, String jsonValue) {
        try {
            producer.send(new ProducerRecord<>(topic, key, jsonValue)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send raw message to topic " + topic, e);
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
        }
    }

    public static String randomId() {
        return UUID.randomUUID().toString();
    }
}
