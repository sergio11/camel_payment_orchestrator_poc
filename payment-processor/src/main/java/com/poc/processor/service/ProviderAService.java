package com.poc.processor.service;

import com.poc.shared.event.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class ProviderAService {

    private static final double FAILURE_RATE = 0.10; // 10% failure
    private static final int MIN_LATENCY_MS = 100;
    private static final int MAX_LATENCY_MS = 200;

    public CompletableFuture<ProviderResponse> processPayment(String paymentId, String amount) {
        return CompletableFuture.supplyAsync(() -> {
            // Simulate latency
            int latency = ThreadLocalRandom.current().nextInt(MIN_LATENCY_MS, MAX_LATENCY_MS + 1);
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ProviderResponse("provider-a", null, false, "INTERRUPTED", "Thread interrupted", LocalDateTime.now());
            }

            // Simulate failure
            if (ThreadLocalRandom.current().nextDouble() < FAILURE_RATE) {
                return new ProviderResponse(
                    "provider-a",
                    UUID.randomUUID().toString(),
                    false,
                    "PROVIDER_ERROR",
                    "Simulated provider A failure",
                    LocalDateTime.now()
                );
            }

            // Success
            return new ProviderResponse(
                "provider-a",
                UUID.randomUUID().toString(),
                true,
                null,
                null,
                LocalDateTime.now()
            );
        });
    }
}