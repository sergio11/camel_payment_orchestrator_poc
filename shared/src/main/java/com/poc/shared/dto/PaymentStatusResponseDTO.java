package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record PaymentStatusResponseDTO(
    @JsonProperty("id")
    String id,

    @JsonProperty("status")
    String status,

    @JsonProperty("last_updated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime lastUpdated
) {}
