package com.poc.processor.processor;

import com.poc.processor.service.ProviderAService;
import com.poc.processor.service.ProviderBService;
import com.poc.processor.service.ProviderCircuitBreakerService;
import com.poc.shared.config.ProviderConfig;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ProviderRouterBean {

    @Inject
    ProviderConfig config;

    @Inject
    ProviderAService providerAService;

    @Inject
    ProviderBService providerBService;

    @Inject
    ProviderCircuitBreakerService circuitBreakerService;

    public String routeToProvider(String exchange) {
        // Dynamic routing logic based on provider health, load, etc.
        // For now, always try Provider A first
        return "direct:provider-a";
    }

    public String routeToFallback(String exchange) {
        return "direct:provider-b";
    }

    public String providerAUrl() {
        return config.providerAUrl();
    }

    public String providerBUrl() {
        return config.providerBUrl();
    }

    // Called from routes to execute provider calls with circuit breaker
    public CompletableFuture<ProviderResponse> callProviderA(PaymentMessage message) {
        return CompletableFuture.supplyAsync(() -> 
            circuitBreakerService.executeWithProviderA(() -> 
                providerAService.processPayment(message.paymentId(), message.amount().toString()).join()
            )
        );
    }

    public CompletableFuture<ProviderResponse> callProviderB(PaymentMessage message) {
        return CompletableFuture.supplyAsync(() -> 
            circuitBreakerService.executeWithProviderB(() -> 
                providerBService.processPayment(message.paymentId(), message.amount().toString()).join()
            )
        );
    }
}