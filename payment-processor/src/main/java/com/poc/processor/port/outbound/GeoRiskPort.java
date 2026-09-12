package com.poc.processor.port.outbound;

public interface GeoRiskPort {
    int calculateGeoRiskScore(String country);
}
