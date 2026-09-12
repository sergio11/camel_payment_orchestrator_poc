package com.poc.processor.service;

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

    @POST
    @Path("/process")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response processPayment(PaymentMessage paymentMessage) {
        String paymentId = paymentMessage.paymentId();
        String amount = paymentMessage.amount() != null ? paymentMessage.amount().toString() : null;

        int latency = ThreadLocalRandom.current().nextInt(minLatencyMs, maxLatencyMs + 1);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.status(500).entity(
                new ProviderResponse(providerIdValue, null, false, "INTERRUPTED", "Thread interrupted", LocalDateTime.now())
            ).build();
        }

        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return Response.status(500).entity(
                new ProviderResponse(
                    providerIdValue,
                    UUID.randomUUID().toString(),
                    false,
                    "PROVIDER_ERROR",
                    "Simulated " + providerIdValue + " failure",
                    LocalDateTime.now()
                )
            ).build();
        }

        return Response.ok(
            new ProviderResponse(
                providerIdValue,
                UUID.randomUUID().toString(),
                true,
                null,
                null,
                LocalDateTime.now()
            )
        ).build();
    }
}
