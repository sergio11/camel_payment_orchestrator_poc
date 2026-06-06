package com.poc.shared.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;

@ApplicationScoped
public class ProviderConfig {

    @ConfigProperty(name = "provider.a.url", defaultValue = "http://localhost:9090/provider-a")
    String providerAUrl;

    @ConfigProperty(name = "provider.b.url", defaultValue = "http://localhost:9091/provider-b")
    String providerBUrl;

    @ConfigProperty(name = "provider.circuit-breaker.failure-threshold", defaultValue = "50")
    int circuitBreakerFailureThreshold;

    @ConfigProperty(name = "provider.circuit-breaker.wait-duration", defaultValue = "5s")
    Duration circuitBreakerWaitDuration;

    @ConfigProperty(name = "provider.max-retries", defaultValue = "5")
    int maxRetries;

    @ConfigProperty(name = "provider.retry-backoff", defaultValue = "2s")
    Duration retryBackoff;

    public String providerAUrl() { return providerAUrl; }
    public String providerBUrl() { return providerBUrl; }
    public int circuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public Duration circuitBreakerWaitDuration() { return circuitBreakerWaitDuration; }
    public int maxRetries() { return maxRetries; }
    public Duration retryBackoff() { return retryBackoff; }
}