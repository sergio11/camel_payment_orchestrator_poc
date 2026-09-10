package com.poc.processor.domain;

public record ProviderGatewayResult(
    String providerId,
    String transactionId,
    boolean success,
    String errorCode,
    String errorMessage
) {
    public static ProviderGatewayResult success(String providerId, String transactionId) {
        return new ProviderGatewayResult(providerId, transactionId, true, null, null);
    }

    public static ProviderGatewayResult failure(String providerId, String errorCode, String errorMessage) {
        return new ProviderGatewayResult(providerId, null, false, errorCode, errorMessage);
    }
}
