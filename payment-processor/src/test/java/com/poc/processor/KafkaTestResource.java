package com.poc.processor;

import java.util.Map;

import com.poc.camel.testsupport.container.KafkaTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    private KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        System.setProperty("testcontainers.ryuk.disabled", "true");
        System.setProperty("ryuk.disabled", "true");
        if (System.getenv("DOCKER_HOST") == null) {
            System.setProperty("docker.host", "npipe:////./pipe/podman-machine-default");
        }

        kafka = KafkaTestContainer.getInstance();
        if (!kafka.isRunning()) {
            kafka.start();
        }

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
        // Singleton compartido entre clases E2E; se detiene con la JVM
    }
}
