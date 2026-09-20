package com.poc.backend.integration;

import com.poc.camel.testsupport.container.KafkaTestContainer;
import com.poc.camel.testsupport.container.PostgresTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

public class BackendIntegrationTest implements QuarkusTestResourceLifecycleManager {

    static {
        System.setProperty("testcontainers.ryuk.disabled", "true");
        System.setProperty("ryuk.disabled", "true");
        if (System.getenv("DOCKER_HOST") == null) {
            System.setProperty("docker.host", "npipe:////./pipe/podman-machine-default");
        }
    }

    private PostgreSQLContainer<?> postgres;
    private KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        postgres = PostgresTestContainer.getInstance();
        if (!postgres.isRunning()) {
            postgres.start();
        }

        kafka = KafkaTestContainer.getInstance();
        if (!kafka.isRunning()) {
            kafka.start();
        }
        String bootstrapServers = kafka.getBootstrapServers();

        return Map.of(
            "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
            "quarkus.datasource.username", postgres.getUsername(),
            "quarkus.datasource.password", postgres.getPassword(),
            "quarkus.datasource.db-kind", "postgresql",
            "kafka.bootstrap.servers", bootstrapServers,
            "camel.component.kafka.brokers", bootstrapServers
        );
    }

    @Override
    public void stop() {
        // Singleton stops with JVM
    }

    @Override
    public int order() {
        return 0;
    }
}
