package com.poc.shared.dto;

import java.util.List;

public record PaymentPageResponse(
    List<PaymentResponse> payments,
    long total,
    int limit,
    int offset
) {}
