package com.poc.processor.port.outbound;

import com.poc.shared.event.PaymentMessage;
import jakarta.ws.rs.core.Response;

public interface PaymentProviderPort {
    Response processPayment(PaymentMessage paymentMessage);
    String providerId();
}
