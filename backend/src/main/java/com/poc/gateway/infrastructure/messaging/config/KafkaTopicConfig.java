package com.poc.gateway.infrastructure.messaging.config;

import com.poc.gateway.domain.model.PaymentStatus;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@ConfigMapping(prefix = "kafka.topic.payments")
public interface KafkaTopicConfig {

    String received();
    String processed();
    String failed();
    String review();

    @WithName("status.changed")
    String statusChanged();

    @WithName("dead-letter")
    String deadLetter();

    String retry();

    default Map<String, PaymentStatus> topicStatusMap() {
        return Map.of(
            processed(), PaymentStatus.APPROVED,
            failed(), PaymentStatus.FAILED,
            review(), PaymentStatus.REVIEW
        );
    }

    default Optional<PaymentStatus> resolveStatus(String topic) {
        return Optional.ofNullable(topicStatusMap().get(topic));
    }
}
