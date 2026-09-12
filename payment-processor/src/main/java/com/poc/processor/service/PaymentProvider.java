package com.poc.processor.service;

import com.poc.shared.event.PaymentMessage;
import jakarta.ws.rs.core.Response;

/**
 * Abstraction for payment providers.
 *
 * @deprecated Use {@link com.poc.processor.port.outbound.PaymentProviderPort} instead.
 */
@Deprecated
public interface PaymentProvider {
    Response processPayment(PaymentMessage paymentMessage);
    String providerId();
}
