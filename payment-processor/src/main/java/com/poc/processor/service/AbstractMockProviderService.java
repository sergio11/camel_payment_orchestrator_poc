package com.poc.processor.service;

import com.poc.processor.domain.ProviderGatewayResult;
import com.poc.processor.port.outbound.PaymentProviderPort;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractMockProviderService implements PaymentProviderPort {

    private final String providerIdValue;
    private final double failureRate;
    private final int minLatencyMs;
    private final int maxLatencyMs;

    protected AbstractMockProviderService(String providerId, double failureRate, int minLatencyMs, int maxLatencyMs) {
        this.providerIdValue = providerId;
        this.failureRate = failureRate;
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
    }

    @Override
    public String providerId() {
        return providerIdValue;
    }

    @Override
    public ProviderGatewayResult processPayment(PaymentMessage paymentMessage) {
        int latency = ThreadLocalRandom.current().nextInt(minLatencyMs, maxLatencyMs + 1);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProviderGatewayResult.failure(providerIdValue, "INTERRUPTED", "Thread interrupted");
        }

        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return ProviderGatewayResult.failure(
                providerIdValue,
                "PROVIDER_ERROR",
                "Simulated " + providerIdValue + " failure"
            );
        }

        return ProviderGatewayResult.success(providerIdValue, UUID.randomUUID().toString());
    }

    @POST
    @Path("/process")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handlePayment(PaymentMessage paymentMessage) {
        ProviderGatewayResult result = processPayment(paymentMessage);
        ProviderResponse providerResponse = new ProviderResponse(
            result.providerId(),
            result.transactionId(),
            result.success(),
            result.errorCode(),
            result.errorMessage(),
            LocalDateTime.now()
        );
        if (result.success()) {
            return Response.ok(providerResponse).build();
        }
        return Response.status(500).entity(providerResponse).build();
    }
}
