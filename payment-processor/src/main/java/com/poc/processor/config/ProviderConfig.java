package com.poc.processor.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;

@ApplicationScoped
public class ProviderConfig {

    @ConfigProperty(name = "provider.a-url")
    String aUrl;

    @ConfigProperty(name = "provider.b-url")
    String bUrl;

    @ConfigProperty(name = "provider.circuit-breaker.failure-threshold")
    int circuitBreakerFailureThreshold;

    @ConfigProperty(name = "provider.circuit-breaker.wait-duration")
    Duration circuitBreakerWaitDuration;

    @ConfigProperty(name = "provider.circuit-breaker.sliding-window-size", defaultValue = "100")
    int circuitBreakerSlidingWindowSize;

    public String providerAUrl() { return aUrl; }
    public String providerBUrl() { return bUrl; }
    public int circuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public Duration circuitBreakerWaitDuration() { return circuitBreakerWaitDuration; }
    public int circuitBreakerSlidingWindowSize() { return circuitBreakerSlidingWindowSize; }
}
