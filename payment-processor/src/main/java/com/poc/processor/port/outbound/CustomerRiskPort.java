package com.poc.processor.port.outbound;

public interface CustomerRiskPort {
    String determineRiskTier(String customerId);
}
