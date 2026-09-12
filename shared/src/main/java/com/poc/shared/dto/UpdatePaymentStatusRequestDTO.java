package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePaymentStatusRequestDTO(
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "PENDING|PROCESSING|APPROVED|REJECTED|FAILED|REVIEW", message = "Invalid status value")
    @JsonProperty("status")
    String status
) {}
