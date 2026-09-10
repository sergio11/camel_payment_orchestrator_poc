package com.poc.gateway.infrastructure.messaging.consumer;

import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentStatusRouterTest {

    @Mock
    PaymentStatusHandler handler;

    @Mock
    KafkaTopicConfig topicConfig;

    @InjectMocks
    PaymentStatusRouter router;

    @Test
    void route_knownTopic_callsHandler() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("processed", 0, 0, "key", "value");
        when(topicConfig.resolveStatus("processed")).thenReturn(Optional.of(PaymentStatus.APPROVED));

        router.route(record);

        verify(handler).processStatusChange(record, PaymentStatus.APPROVED);
    }

    @Test
    void route_failedTopic_callsHandlerWithFailed() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("failed", 0, 0, "key", "value");
        when(topicConfig.resolveStatus("failed")).thenReturn(Optional.of(PaymentStatus.FAILED));

        router.route(record);

        verify(handler).processStatusChange(record, PaymentStatus.FAILED);
    }

    @Test
    void route_reviewTopic_callsHandlerWithReview() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("review", 0, 0, "key", "value");
        when(topicConfig.resolveStatus("review")).thenReturn(Optional.of(PaymentStatus.REVIEW));

        router.route(record);

        verify(handler).processStatusChange(record, PaymentStatus.REVIEW);
    }

    @Test
    void route_unknownTopic_sendsToDlq() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("unknown", 0, 0, "key", "value");
        when(topicConfig.resolveStatus("unknown")).thenReturn(Optional.empty());

        router.route(record);

        verify(handler).sendToDeadLetter(eq(record), anyString());
        verify(handler, never()).processStatusChange(any(), any());
    }
}
