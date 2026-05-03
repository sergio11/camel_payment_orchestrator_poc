package com.poc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
    String error,
    String message,
    List<ErrorDetail> details,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime timestamp
) {
    public static ErrorResponse from(String error, String message) {
        return new ErrorResponse(error, message, null, LocalDateTime.now());
    }

    public static ErrorResponse from(String error, String message, List<ErrorDetail> details) {
        return new ErrorResponse(error, message, details, LocalDateTime.now());
    }

    public record ErrorDetail(String field, String message) {}
}