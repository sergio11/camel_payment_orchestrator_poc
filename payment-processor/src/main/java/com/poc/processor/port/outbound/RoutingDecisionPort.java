package com.poc.processor.port.outbound;

import java.math.BigDecimal;

public interface RoutingDecisionPort {
    String resolveFraudRoute(BigDecimal amount, String paymentMethod, String country);
}
