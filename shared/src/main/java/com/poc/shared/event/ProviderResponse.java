package com.poc.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record ProviderResponse(
    String providerId,
    String transactionId,
    boolean success,
    String errorCode,
    String errorMessage,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime processedAt
) {}