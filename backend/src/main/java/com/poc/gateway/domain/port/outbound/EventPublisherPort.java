package com.poc.gateway.domain.port.outbound;

import com.poc.gateway.domain.model.PaymentReceivedEvent;

public interface EventPublisherPort {
    boolean publishPaymentReceived(PaymentReceivedEvent event);

    boolean publishStatusChanged(String paymentId, String status);

    boolean publishDeadLetter(String paymentId, String payload, String reason);
}
