package com.poc.processor.port.outbound;

import com.poc.processor.domain.ProviderGatewayResult;
import com.poc.shared.event.PaymentMessage;

public interface PaymentProviderPort {
    ProviderGatewayResult processPayment(PaymentMessage paymentMessage);
    String providerId();
}
