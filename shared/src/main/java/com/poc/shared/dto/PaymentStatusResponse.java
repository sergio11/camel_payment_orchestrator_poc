package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
    String id,
    String status,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime updatedAt
) {}