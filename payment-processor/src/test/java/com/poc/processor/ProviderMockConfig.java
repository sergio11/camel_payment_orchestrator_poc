package com.poc.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class ProviderMockConfig {

    private volatile boolean providerASucceeds = false;
    private volatile boolean providerBSucceeds = false;
    final AtomicInteger providerACallCount = new AtomicInteger(0);

    public boolean isProviderASucceeds() {
        return providerASucceeds;
    }

    public void setProviderASucceeds(boolean providerASucceeds) {
        this.providerASucceeds = providerASucceeds;
    }

    public boolean isProviderBSucceeds() {
        return providerBSucceeds;
    }

    public void setProviderBSucceeds(boolean providerBSucceeds) {
        this.providerBSucceeds = providerBSucceeds;
    }

    public int getAndResetProviderACallCount() {
        return providerACallCount.getAndSet(0);
    }

    public int getProviderACallCount() {
        return providerACallCount.get();
    }

    public void resetCallCount() {
        providerACallCount.set(0);
    }

    public void incrementProviderACallCount() {
        providerACallCount.incrementAndGet();
    }

    public String providerAFailureResponse() {
        return """
            {
                "providerId": "provider-a",
                "transactionId": null,
                "success": false,
                "errorCode": "PROVIDER_A_DOWN",
                "errorMessage": "Provider A is unavailable",
                "processedAt": "2026-01-01T00:00:00"
            }
            """;
    }

    public String providerBFailureResponse() {
        return """
            {
                "providerId": "provider-b",
                "transactionId": null,
                "success": false,
                "errorCode": "PROVIDER_B_DOWN",
                "errorMessage": "Provider B is unavailable",
                "processedAt": "2026-01-01T00:00:00"
            }
            """;
    }

    public String providerBSuccessResponse() {
        return """
            {
                "providerId": "provider-b",
                "transactionId": "txn-123",
                "success": true,
                "errorCode": null,
                "errorMessage": null,
                "processedAt": "2026-01-01T00:00:00"
            }
            """;
    }

    public String providerSuccessResponse(String providerId) {
        return """
            {
                "providerId": "%s",
                "transactionId": "txn-test-%s",
                "success": true,
                "errorCode": null,
                "errorMessage": null,
                "processedAt": "2026-01-01T00:00:00"
            }
            """.formatted(providerId, UUID.randomUUID());
    }
}
