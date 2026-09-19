package com.poc.gateway.infrastructure.messaging.config;

import com.poc.gateway.domain.port.outbound.PaymentEventTopicPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KafkaPaymentEventTopicAdapter implements PaymentEventTopicPort {

    @Inject
    KafkaTopicConfig topicConfig;

    @Override
    public String receivedTopic() {
        return topicConfig.received();
    }
}
