package com.poc.gateway.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {

    @Test
    void create_returnsEventWithPendingStatus() {
        UUID aggId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.create(aggId, "type", "payload", "key123");
        assertNotNull(event.id());
        assertEquals(aggId, event.aggregateId());
        assertEquals("type", event.type());
        assertEquals("payload", event.payload());
        assertEquals(OutboxStatus.PENDING, event.status());
        assertEquals("key123", event.idempotencyKey());
        assertNotNull(event.createdAt());
    }

    @Test
    void create_generatesUniqueIds() {
        OutboxEvent e1 = OutboxEvent.create(UUID.randomUUID(), "t", "p", "k");
        OutboxEvent e2 = OutboxEvent.create(UUID.randomUUID(), "t", "p", "k");
        assertNotEquals(e1.id(), e2.id());
    }

    @Test
    void withSent_returnsEventWithSentStatus() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "type", "payload", "key");
        OutboxEvent sent = event.withSent();
        assertEquals(OutboxStatus.SENT, sent.status());
        assertEquals(event.id(), sent.id());
        assertEquals(event.aggregateId(), sent.aggregateId());
    }

    @Test
    void withSent_preservesAllOtherFields() {
        UUID aggId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.create(aggId, "type", "payload", "key");
        OutboxEvent sent = event.withSent();
        assertEquals(aggId, sent.aggregateId());
        assertEquals("type", sent.type());
        assertEquals("payload", sent.payload());
        assertEquals("key", sent.idempotencyKey());
        assertEquals(event.createdAt(), sent.createdAt());
    }

    @Test
    void recordEquality() {
        LocalDateTime now = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        OutboxEvent e1 = new OutboxEvent(id, aggId, "t", "p", OutboxStatus.PENDING, now, "k");
        OutboxEvent e2 = new OutboxEvent(id, aggId, "t", "p", OutboxStatus.PENDING, now, "k");
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void recordInequality_differentId() {
        LocalDateTime now = LocalDateTime.now();
        UUID aggId = UUID.randomUUID();
        OutboxEvent e1 = new OutboxEvent(UUID.randomUUID(), aggId, "t", "p", OutboxStatus.PENDING, now, "k");
        OutboxEvent e2 = new OutboxEvent(UUID.randomUUID(), aggId, "t", "p", OutboxStatus.PENDING, now, "k");
        assertNotEquals(e1, e2);
    }

    @Test
    void recordToString_containsFields() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "PaymentCreated", "{}", "key1");
        String str = event.toString();
        assertTrue(str.contains("PaymentCreated"));
        assertTrue(str.contains("key1"));
    }
}
