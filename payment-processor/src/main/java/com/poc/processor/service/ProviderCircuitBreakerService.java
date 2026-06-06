package com.poc.processor.service;

import com.poc.shared.config.ProviderConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.function.Supplier;

@ApplicationScoped
public class ProviderCircuitBreakerService {

    private final CircuitBreaker providerACircuitBreaker;
    private final CircuitBreaker providerBCircuitBreaker;
    private final Retry providerRetry;

    @Inject
    public ProviderCircuitBreakerService(ProviderConfig config) {
        // Circuit Breaker Config
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.circuitBreakerFailureThreshold())
            .waitDurationInOpenState(config.circuitBreakerWaitDuration())
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        providerACircuitBreaker = CircuitBreaker.of("providerA", cbConfig);
        providerBCircuitBreaker = CircuitBreaker.of("providerB", cbConfig);

        // Retry Config - simple fixed wait duration (compatible with 2.2.x)
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(config.maxRetries())
            .waitDuration(config.retryBackoff())
            .retryExceptions(java.io.IOException.class, java.util.concurrent.TimeoutException.class)
            .build();
        providerRetry = Retry.of("providerRetry", retryConfig);
    }

    public <T> T executeWithProviderA(Supplier<T> supplier) {
        return CircuitBreaker.decorateSupplier(providerACircuitBreaker, 
            Retry.decorateSupplier(providerRetry, supplier))
            .get();
    }

    public <T> T executeWithProviderB(Supplier<T> supplier) {
        return CircuitBreaker.decorateSupplier(providerBCircuitBreaker, supplier)
            .get();
    }

    public CircuitBreaker getProviderACircuitBreaker() {
        return providerACircuitBreaker;
    }

    public CircuitBreaker getProviderBCircuitBreaker() {
        return providerBCircuitBreaker;
    }
}