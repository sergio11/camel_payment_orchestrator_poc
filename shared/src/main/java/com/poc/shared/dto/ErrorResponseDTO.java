package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponseDTO(
    @JsonProperty("error")
    String error,

    @JsonProperty("message")
    String message,

    @JsonProperty("details")
    List<ErrorDetailDTO> details,

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    LocalDateTime timestamp
) {
    public static ErrorResponseDTO from(String error, String message) {
        return new ErrorResponseDTO(error, message, null, LocalDateTime.now());
    }

    public static ErrorResponseDTO from(String error, String message, List<ErrorDetailDTO> details) {
        return new ErrorResponseDTO(error, message, details, LocalDateTime.now());
    }
}
