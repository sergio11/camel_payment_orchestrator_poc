package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PaymentPageResponseDTO(
    @JsonProperty("payments")
    List<PaymentResponseDTO> payments,

    @JsonProperty("total")
    long total,

    @JsonProperty("limit")
    int limit,

    @JsonProperty("offset")
    int offset
) {}
