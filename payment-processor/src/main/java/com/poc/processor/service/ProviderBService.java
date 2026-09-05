package com.poc.processor.service;

import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated mock provider for the {@code mock} profile.
 * Not a real HTTP integration: returns canned success/failure with latency.
 */
@ApplicationScoped
@Path("/provider-b")
public class ProviderBService implements PaymentProvider {

    private static final double FAILURE_RATE = 0.02; // 2% failure
    private static final int MIN_LATENCY_MS = 500;
    private static final int MAX_LATENCY_MS = 1000;

    @Override
    public String providerId() {
        return "provider-b";
    }

    @POST
    @Path("/process")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response processPayment(PaymentMessage paymentMessage) {
        String paymentId = paymentMessage.paymentId();
        String amount = paymentMessage.amount() != null ? paymentMessage.amount().toString() : null;

        // Simulate latency
        int latency = ThreadLocalRandom.current().nextInt(MIN_LATENCY_MS, MAX_LATENCY_MS + 1);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.status(500).entity(
                new ProviderResponse("provider-b", null, false, "INTERRUPTED", "Thread interrupted", LocalDateTime.now())
            ).build();
        }

        // Simulate failure
        if (ThreadLocalRandom.current().nextDouble() < FAILURE_RATE) {
            return Response.status(500).entity(
                new ProviderResponse(
                    "provider-b",
                    UUID.randomUUID().toString(),
                    false,
                    "PROVIDER_ERROR",
                    "Simulated provider B failure",
                    LocalDateTime.now()
                )
            ).build();
        }

        // Success
        return Response.ok(
            new ProviderResponse(
                "provider-b",
                UUID.randomUUID().toString(),
                true,
                null,
                null,
                LocalDateTime.now()
            )
        ).build();
    }
}
