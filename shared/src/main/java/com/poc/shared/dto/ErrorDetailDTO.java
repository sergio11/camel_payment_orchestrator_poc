package com.poc.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorDetailDTO(
    @JsonProperty("field")
    String field,

    @JsonProperty("message")
    String message
) {}
