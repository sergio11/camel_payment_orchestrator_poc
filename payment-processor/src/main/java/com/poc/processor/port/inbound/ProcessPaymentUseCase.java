package com.poc.processor.port.inbound;

import com.poc.shared.event.PaymentMessage;

public interface ProcessPaymentUseCase {
    void execute(PaymentMessage message);
}
