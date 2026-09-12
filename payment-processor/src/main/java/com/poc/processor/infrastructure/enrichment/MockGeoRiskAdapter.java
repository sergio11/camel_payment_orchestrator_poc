package com.poc.processor.infrastructure.enrichment;

import com.poc.processor.port.outbound.GeoRiskPort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MockGeoRiskAdapter implements GeoRiskPort {

    @Override
    public int calculateGeoRiskScore(String country) {
        if (country == null) return 0;
        return switch (country) {
            case "XX", "YY" -> 80;
            case "ZZ", "WW" -> 50;
            default -> 10;
        };
    }
}
