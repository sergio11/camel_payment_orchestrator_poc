package com.poc.gateway.domain.port.outbound;

import com.poc.gateway.domain.Payment;

public interface PaymentEventSerializer {
    String serialize(Payment payment);
}
