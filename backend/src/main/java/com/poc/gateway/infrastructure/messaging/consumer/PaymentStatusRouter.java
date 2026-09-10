package com.poc.gateway.infrastructure.messaging.consumer;

import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentStatusRouter {

    private static final Logger LOG = Logger.getLogger(PaymentStatusRouter.class);

    @Inject
    PaymentStatusHandler handler;

    @Inject
    KafkaTopicConfig topicConfig;

    public void route(ConsumerRecord<String, String> record) {
        Optional<PaymentStatus> status = topicConfig.resolveStatus(record.topic());
        if (status.isEmpty()) {
            LOG.warnf("Unknown topic: %s, sending to DLQ", record.topic());
            handler.sendToDeadLetter(record, "Unknown topic: " + record.topic());
            return;
        }
        handler.processStatusChange(record, status.get());
    }
}
