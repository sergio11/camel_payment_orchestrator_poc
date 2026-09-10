package com.poc.processor.port.inbound;

import com.poc.shared.event.PaymentMessage;

public interface EnrichPaymentUseCase {
    PaymentMessage enrich(PaymentMessage message);
}
