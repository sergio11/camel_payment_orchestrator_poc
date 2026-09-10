package com.poc.gateway.infrastructure.messaging.config;

import com.poc.gateway.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KafkaTopicConfigTest {

    private KafkaTopicConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaTopicConfig() {
            @Override public String received() { return "payments.received"; }
            @Override public String processed() { return "payments.processed"; }
            @Override public String failed() { return "payments.failed"; }
            @Override public String review() { return "payments.review"; }
            @Override public String statusChanged() { return "payments.status.changed"; }
            @Override public String deadLetter() { return "payments.dlq"; }
            @Override public String retry() { return "payments.retry"; }
        };
    }

    @Test
    void topicStatusMap_containsProcessedTopic() {
        Map<String, PaymentStatus> map = config.topicStatusMap();
        assertEquals(PaymentStatus.APPROVED, map.get("payments.processed"));
    }

    @Test
    void topicStatusMap_containsFailedTopic() {
        Map<String, PaymentStatus> map = config.topicStatusMap();
        assertEquals(PaymentStatus.FAILED, map.get("payments.failed"));
    }

    @Test
    void topicStatusMap_containsReviewTopic() {
        Map<String, PaymentStatus> map = config.topicStatusMap();
        assertEquals(PaymentStatus.REVIEW, map.get("payments.review"));
    }

    @Test
    void topicStatusMap_hasThreeEntries() {
        Map<String, PaymentStatus> map = config.topicStatusMap();
        assertEquals(3, map.size());
    }

    @Test
    void topicStatusMap_doesNotContainReceivedTopic() {
        Map<String, PaymentStatus> map = config.topicStatusMap();
        assertNull(map.get("payments.received"));
    }

    @Test
    void topicStatusMap_doesNotContainDlqTopic() {
        Map<String, PaymentStatus> map = config.topicStatusMap();
        assertNull(map.get("payments.dlq"));
    }

    @Test
    void resolveStatus_processedReturnsApproved() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.processed");
        assertTrue(result.isPresent());
        assertEquals(PaymentStatus.APPROVED, result.get());
    }

    @Test
    void resolveStatus_failedReturnsFailed() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.failed");
        assertTrue(result.isPresent());
        assertEquals(PaymentStatus.FAILED, result.get());
    }

    @Test
    void resolveStatus_reviewReturnsReview() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.review");
        assertTrue(result.isPresent());
        assertEquals(PaymentStatus.REVIEW, result.get());
    }

    @Test
    void resolveStatus_unknownTopicReturnsEmpty() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.unknown");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveStatus_receivedTopicReturnsEmpty() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.received");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveStatus_dlqTopicReturnsEmpty() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.dlq");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveStatus_statusChangedReturnsEmpty() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.status.changed");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveStatus_retryReturnsEmpty() {
        Optional<PaymentStatus> result = config.resolveStatus("payments.retry");
        assertTrue(result.isEmpty());
    }

    @Test
    void accessorMethods_returnConfiguredValues() {
        assertEquals("payments.received", config.received());
        assertEquals("payments.processed", config.processed());
        assertEquals("payments.failed", config.failed());
        assertEquals("payments.review", config.review());
        assertEquals("payments.status.changed", config.statusChanged());
        assertEquals("payments.dlq", config.deadLetter());
        assertEquals("payments.retry", config.retry());
    }
}
