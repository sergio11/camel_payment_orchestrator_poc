package com.poc.processor.port.outbound;

import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;

public interface PaymentEventPublisherPort {
    void publishProcessed(PaymentMessage originalMessage, ProviderResponse providerResponse);
    void publishFailed(PaymentMessage originalMessage, String reason);
    void publishRetry(PaymentMessage originalMessage);
    void publishDeadLetter(PaymentMessage originalMessage, String reason);
}
