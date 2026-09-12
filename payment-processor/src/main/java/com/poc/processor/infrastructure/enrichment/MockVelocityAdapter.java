package com.poc.processor.infrastructure.enrichment;

import com.poc.processor.port.outbound.VelocityPort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MockVelocityAdapter implements VelocityPort {

    @Override
    public int calculateVelocityScore(String customerId) {
        return Math.floorMod(customerId.hashCode(), 100);
    }
}
