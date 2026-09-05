package com.poc.gateway.health;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;

@Readiness
@ApplicationScoped
public class SimpleHealthCheck implements HealthCheck {

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "")
    String bootstrapServers;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Payment Gateway Health Check");
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            builder.up();
            return builder.build();
        }
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                        AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000))) {
            admin.listTopics().listings().get(5, TimeUnit.SECONDS);
            builder.up().withData("kafka", "reachable");
        } catch (Exception e) {
            builder.down().withData("kafka", e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return builder.build();
    }
}
