package com.poc.gateway.infrastructure.persistence.mapper;

import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.OutboxStatus;
import com.poc.gateway.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventMapperTest {

    private final OutboxEventMapper mapper = new OutboxEventMapperImpl();

    @Test
    void toEntity_nullDomain_returnsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void toDomain_nullEntity_returnsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    void toEntity_pendingStatus() {
        OutboxEvent domain = new OutboxEvent(
            UUID.randomUUID(), UUID.randomUUID(), "type", "payload",
            OutboxStatus.PENDING, LocalDateTime.now(), "key"
        );

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertEquals(OutboxStatus.PENDING, entity.status);
    }

    @Test
    void toEntity_sentStatus() {
        OutboxEvent domain = new OutboxEvent(
            UUID.randomUUID(), UUID.randomUUID(), "type", "payload",
            OutboxStatus.SENT, LocalDateTime.now(), "key"
        );

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertEquals(OutboxStatus.SENT, entity.status);
    }

    @Test
    void toDomain_pendingStatus() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "t";
        entity.payload = "p";
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);

        assertEquals(OutboxStatus.PENDING, domain.status());
    }

    @Test
    void toDomain_sentStatus() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "t";
        entity.payload = "p";
        entity.status = OutboxStatus.SENT;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);

        assertEquals(OutboxStatus.SENT, domain.status());
    }

    @Test
    void toEntity_and_toDomain_roundtrip_pending() {
        UUID id = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent domain = new OutboxEvent(id, aggId, "type", "payload", OutboxStatus.PENDING, now, "key");

        OutboxEventEntity entity = mapper.toEntity(domain);
        assertEquals(id, entity.id);
        assertEquals(aggId, entity.aggregateId);
        assertEquals("type", entity.type);
        assertEquals("payload", entity.payload);
        assertEquals(OutboxStatus.PENDING, entity.status);
        assertEquals(now, entity.createdAt);
        assertEquals("key", entity.idempotencyKey);

        OutboxEvent back = mapper.toDomain(entity);
        assertEquals(id, back.id());
        assertEquals(aggId, back.aggregateId());
        assertEquals("type", back.type());
        assertEquals("payload", back.payload());
        assertEquals(OutboxStatus.PENDING, back.status());
        assertEquals(now, back.createdAt());
        assertEquals("key", back.idempotencyKey());
    }

    @Test
    void toEntity_and_toDomain_roundtrip_sent() {
        UUID id = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent domain = new OutboxEvent(id, aggId, "EventType", "{\"data\":true}", OutboxStatus.SENT, now, "idem-123");

        OutboxEventEntity entity = mapper.toEntity(domain);
        assertEquals(OutboxStatus.SENT, entity.status);

        OutboxEvent back = mapper.toDomain(entity);
        assertEquals(OutboxStatus.SENT, back.status());
        assertEquals("EventType", back.type());
        assertEquals("{\"data\":true}", back.payload());
    }

    @Test
    void toEntity_convertsSentStatus() {
        OutboxEvent domain = OutboxEvent.create(UUID.randomUUID(), "t", "p", "k").withSent();
        OutboxEventEntity entity = mapper.toEntity(domain);
        assertEquals(OutboxStatus.SENT, entity.status);
    }

    @Test
    void toDomain_convertsSentStatus() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "t";
        entity.payload = "p";
        entity.status = OutboxStatus.SENT;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);
        assertEquals(OutboxStatus.SENT, domain.status());
    }

    @Test
    void toEntity_nullIdempotencyKey() {
        UUID id = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        OutboxEvent domain = new OutboxEvent(id, aggId, "type", "payload", OutboxStatus.PENDING, LocalDateTime.now(), null);

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertNull(entity.idempotencyKey);
        assertEquals(id, entity.id);
    }

    @Test
    void toDomain_nullIdempotencyKey() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "t";
        entity.payload = "p";
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = null;

        OutboxEvent domain = mapper.toDomain(entity);

        assertNull(domain.idempotencyKey());
    }

    @Test
    void toEntity_preservesAllFields() {
        UUID id = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        OutboxEvent domain = new OutboxEvent(id, aggId, "MyType", "myPayload", OutboxStatus.PENDING, now, "idemKey");

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertEquals(id, entity.id);
        assertEquals(aggId, entity.aggregateId);
        assertEquals("MyType", entity.type);
        assertEquals("myPayload", entity.payload);
        assertEquals(now, entity.createdAt);
        assertEquals("idemKey", entity.idempotencyKey);
    }

    @Test
    void toDomain_preservesAllFields() {
        OutboxEventEntity entity = new OutboxEventEntity();
        UUID id = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        entity.id = id;
        entity.aggregateId = aggId;
        entity.type = "EventType";
        entity.payload = "{\"key\":\"value\"}";
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = now;
        entity.idempotencyKey = "idemKey";

        OutboxEvent domain = mapper.toDomain(entity);

        assertEquals(id, domain.id());
        assertEquals(aggId, domain.aggregateId());
        assertEquals("EventType", domain.type());
        assertEquals("{\"key\":\"value\"}", domain.payload());
        assertEquals(OutboxStatus.PENDING, domain.status());
        assertEquals(now, domain.createdAt());
        assertEquals("idemKey", domain.idempotencyKey());
    }

    @Test
    void toEntity_createFactory_setsPendingByDefault() {
        UUID aggId = UUID.randomUUID();
        OutboxEvent domain = OutboxEvent.create(aggId, "Type", "Payload", "Key");

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertEquals(OutboxStatus.PENDING, entity.status);
        assertEquals(aggId, entity.aggregateId);
        assertNotNull(entity.id);
    }

    @Test
    void toDomain_emptyStrings() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "";
        entity.payload = "";
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "";

        OutboxEvent domain = mapper.toDomain(entity);

        assertEquals("", domain.type());
        assertEquals("", domain.payload());
        assertEquals("", domain.idempotencyKey());
    }

    @Test
    void toEntity_nullCreatedAt() {
        OutboxEvent domain = new OutboxEvent(
            UUID.randomUUID(), UUID.randomUUID(), "type", "payload",
            OutboxStatus.PENDING, null, "key"
        );

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertNull(entity.createdAt);
    }

    @Test
    void toDomain_nullCreatedAt() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "t";
        entity.payload = "p";
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = null;
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);

        assertNull(domain.createdAt());
    }

    @Test
    void toEntity_nullTypeAndPayload() {
        OutboxEvent domain = new OutboxEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, null,
            OutboxStatus.PENDING, LocalDateTime.now(), "key"
        );

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertNull(entity.type);
        assertNull(entity.payload);
    }

    @Test
    void toDomain_nullTypeAndPayload() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = null;
        entity.payload = null;
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);

        assertNull(domain.type());
        assertNull(domain.payload());
    }

    @Test
    void toEntity_nullAggregateId() {
        OutboxEvent domain = new OutboxEvent(
            UUID.randomUUID(), null, "type", "payload",
            OutboxStatus.PENDING, LocalDateTime.now(), "key"
        );

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertNull(entity.aggregateId);
    }

    @Test
    void toDomain_nullAggregateId() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = null;
        entity.type = "t";
        entity.payload = "p";
        entity.status = OutboxStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);

        assertNull(domain.aggregateId());
    }

    @Test
    void toEntity_nullStatus() {
        OutboxEvent domain = new OutboxEvent(
            UUID.randomUUID(), UUID.randomUUID(), "type", "payload",
            null, LocalDateTime.now(), "key"
        );

        OutboxEventEntity entity = mapper.toEntity(domain);

        assertNull(entity.status);
    }

    @Test
    void toDomain_nullStatus() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.aggregateId = UUID.randomUUID();
        entity.type = "t";
        entity.payload = "p";
        entity.status = null;
        entity.createdAt = LocalDateTime.now();
        entity.idempotencyKey = "k";

        OutboxEvent domain = mapper.toDomain(entity);

        assertNull(domain.status());
    }

    @Test
    void roundTrip_allStatuses() {
        for (OutboxStatus status : OutboxStatus.values()) {
            UUID id = UUID.randomUUID();
            UUID aggId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();
            OutboxEvent domain = new OutboxEvent(id, aggId, "type", "payload", status, now, "key");

            OutboxEventEntity entity = mapper.toEntity(domain);
            OutboxEvent back = mapper.toDomain(entity);

            assertEquals(status, back.status(), "Round-trip failed for " + status);
            assertEquals(id, back.id());
            assertEquals(aggId, back.aggregateId());
        }
    }
}
