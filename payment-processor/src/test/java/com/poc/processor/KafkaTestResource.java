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
        System.setProperty("testcontainers.ryuk.disabled", "true");
        System.setProperty("ryuk.disabled", "true");
        if (System.getenv("DOCKER_HOST") == null) {
            System.setProperty("docker.host", "npipe:////./pipe/podman-machine-default");
        }

        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withStartupTimeout(Duration.ofMinutes(3));
        kafka.start();

        String bootstrapServers = kafka.getBootstrapServers();
        System.setProperty("camel.component.kafka.brokers", bootstrapServers);
        return Map.of(
            "kafka.bootstrap.servers", bootstrapServers,
            "camel.component.kafka.brokers", bootstrapServers,
            "camel.route.payment-processor.auto-startup", "true"
        );
    }

    @Override
    public void stop() {
        if (kafka != null) {
            kafka.stop();
        }
    }
}
