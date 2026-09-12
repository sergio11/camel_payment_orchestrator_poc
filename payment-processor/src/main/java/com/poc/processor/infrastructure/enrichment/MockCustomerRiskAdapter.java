package com.poc.processor.infrastructure.enrichment;

import com.poc.processor.port.outbound.CustomerRiskPort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MockCustomerRiskAdapter implements CustomerRiskPort {

    @Override
    public String determineRiskTier(String customerId) {
        int hash = Math.floorMod(customerId.hashCode(), 3);
        return switch (hash) {
            case 0 -> "LOW";
            case 1 -> "MEDIUM";
            default -> "HIGH";
        };
    }
}
