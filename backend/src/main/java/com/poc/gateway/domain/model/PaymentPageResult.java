package com.poc.gateway.domain.model;

import com.poc.gateway.domain.Payment;
import java.util.List;

public record PaymentPageResult(List<Payment> payments, long total, int limit, int offset) {
}
