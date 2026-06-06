package com.poc.shared.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import java.time.Duration;

@ApplicationScoped
@ConfigProperties(prefix = "provider")
public class ProviderConfig {

    String aUrl;
    String bUrl;
    int circuitBreakerFailureThreshold;
    Duration circuitBreakerWaitDuration;
    int maxRetries;
    Duration retryBackoff;

    public String providerAUrl() { return aUrl; }
    public String providerBUrl() { return bUrl; }
    public int circuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public Duration circuitBreakerWaitDuration() { return circuitBreakerWaitDuration; }
    public int maxRetries() { return maxRetries; }
    public Duration retryBackoff() { return retryBackoff; }
}