package com.poc.processor;

import java.time.Duration;
import java.util.Map;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    private KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withStartupTimeout(Duration.ofMinutes(3));
        kafka.start();

        String bootstrapServers = kafka.getBootstrapServers();
        return Map.of(
            "kafka.bootstrap.servers", bootstrapServers,
            "camel.component.kafka.brokers", bootstrapServers
        );
    }

    @Override
    public void stop() {
        if (kafka != null) {
            kafka.stop();
        }
    }
}
