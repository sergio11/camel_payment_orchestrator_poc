package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.entity.OutboxEventEntity;
import com.poc.gateway.entity.OutboxStatus;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.repository.OutboxEventRepository;
import com.poc.gateway.repository.PaymentRepository;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.gateway.mapper.PaymentMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceBranchesTest {

    @Mock
    PaymentRepository repository;

    @Mock
    KafkaEventPublisher kafkaEventPublisher;

    @Mock
    EntityManager em;

    private PaymentService service;
    private OutboxEventRepository outbox;
    private ObjectMapper objectMapper;

    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        service = new PaymentService();
        outbox = new OutboxEventRepository();
        outbox.em = null;
        objectMapper = new ObjectMapper();
        setServiceField("repository", repository);
        setServiceField("outbox", outbox);
        setServiceField("kafkaEventPublisher", kafkaEventPublisher);
        setServiceField("objectMapper", objectMapper);
        setServiceField("paymentMapper", PaymentMapper.INSTANCE);

        payment = new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING, null, null,
            Map.of(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void setServiceField(String name, Object value) throws Exception {
        Field f = PaymentService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private PaymentRequestDTO request() {
        return new PaymentRequestDTO(new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", Map.of());
    }

    private void stubHappySave() {
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class), anyString())).thenReturn(payment);
        when(kafkaEventPublisher.publishPaymentReceived(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    // ========== createPayment publish failure ==========

    @Test
    @DisplayName("createPayment throws when Kafka publish fails")
    void createPayment_publishFalse_throws() {
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class), anyString())).thenReturn(payment);
        when(kafkaEventPublisher.publishPaymentReceived(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.createPayment(request(), "key-pub-fail"));
        assertTrue(ex.getMessage().contains(payment.id().toString()));
    }

    // ========== getByIdempotencyKey / findExistingPayment ==========

    @Test
    @DisplayName("getByIdempotencyKey returns empty for unknown key")
    void getByIdempotencyKey_unknown_empty() {
        assertTrue(service.getByIdempotencyKey("unknown-key").isEmpty());
        verify(repository).findByIdempotencyKey("unknown-key");
    }

    @Test
    @DisplayName("findExistingPayment returns empty for blank key")
    void findExistingPayment_blank_empty() {
        assertTrue(service.findExistingPayment(null).isEmpty());
        assertTrue(service.findExistingPayment("  ").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("findExistingPayment resolves via outbox event")
    void findExistingPayment_outboxHit_resolves() throws Exception {
        OutboxEventEntity evt = new OutboxEventEntity();
        evt.id = UUID.randomUUID();
        evt.aggregateId = payment.id();
        OutboxEventRepository spy = spy(outbox);
        doReturn(Optional.of(evt)).when(spy).findByIdempotencyKey("key-outbox");
        setServiceField("outbox", spy);
        when(repository.findById(payment.id())).thenReturn(Optional.of(payment));

        Optional<Payment> found = service.findExistingPayment("key-outbox");
        assertTrue(found.isPresent());
        assertEquals(payment.id(), found.get().id());
    }

    @Test
    @DisplayName("findExistingPayment falls through when outbox aggregate is null")
    void findExistingPayment_outboxNullAggregate_fallsThrough() {
        OutboxEventEntity evt = new OutboxEventEntity();
        evt.id = UUID.randomUUID();
        evt.aggregateId = null;
        OutboxEventRepository spy = spy(outbox);
        doReturn(Optional.of(evt)).when(spy).findByIdempotencyKey("key-null-agg");
        try {
            setServiceField("outbox", spy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(repository.findByIdempotencyKey("key-null-agg")).thenReturn(Optional.of(payment));

        assertTrue(service.findExistingPayment("key-null-agg").isPresent());
    }

    @Test
    @DisplayName("findExistingPayment tolerates outbox lookup failure")
    void findExistingPayment_outboxThrows_fallsThrough() {
        OutboxEventRepository spy = spy(outbox);
        doThrow(new RuntimeException("db down")).when(spy).findByIdempotencyKey("key-boom");
        try {
            setServiceField("outbox", spy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(repository.findByIdempotencyKey("key-boom")).thenReturn(Optional.empty());

        assertTrue(service.findExistingPayment("key-boom").isEmpty());
    }

    @Test
    @DisplayName("findExistingPayment tolerates repository lookup failure")
    void findExistingPayment_repoThrows_empty() {
        when(repository.findByIdempotencyKey("key-boom2")).thenThrow(new RuntimeException("db down"));
        assertTrue(service.findExistingPayment("key-boom2").isEmpty());
    }

    // ========== normalizeKey / overloads ==========

    @Test
    @DisplayName("normalizeKey generates UUID for blank")
    void normalizeKey_blank_random() {
        String k1 = service.normalizeKey(null);
        String k2 = service.normalizeKey("  ");
        assertDoesNotThrow(() -> UUID.fromString(k1));
        assertDoesNotThrow(() -> UUID.fromString(k2));
        assertEquals("abc", service.normalizeKey("  abc  "));
    }

    @Test
    @DisplayName("persistWithOutbox single-arg overload delegates")
    void persistWithOutbox_singleArg_delegates() {
        when(repository.save(any(Payment.class), anyString())).thenReturn(payment);
        Payment saved = service.persistWithOutbox(payment);
        assertEquals(payment.id(), saved.id());
    }

    // ========== persistWithOutbox with EM-backed outbox ==========

    @Test
    @DisplayName("persistWithOutbox persists outbox event when EM present")
    void persistWithOutbox_emPresent_persists() throws Exception {
        OutboxEventRepository dbOutbox = new OutboxEventRepository();
        dbOutbox.em = em;
        setServiceField("outbox", dbOutbox);
        when(repository.save(any(Payment.class), anyString())).thenReturn(payment);

        Payment saved = service.persistWithOutbox(payment, "key-em");
        assertEquals(payment.id(), saved.id());
        verify(em).persist(any(OutboxEventEntity.class));
        verify(em).flush();
    }

    // ========== markOutboxSent ==========

    @Test
    @DisplayName("markOutboxSent marks matching pending events")
    void markOutboxSent_matching_marks() throws Exception {
        OutboxEventRepository dbOutbox = spy(new OutboxEventRepository());
        dbOutbox.em = em;
        OutboxEventEntity evt = new OutboxEventEntity();
        evt.id = UUID.randomUUID();
        evt.aggregateId = payment.id();
        doReturn(List.of(evt)).when(dbOutbox).findPending(100);
        setServiceField("outbox", dbOutbox);
        when(em.find(OutboxEventEntity.class, evt.id)).thenReturn(evt);

        service.markOutboxSent(payment.id());

        verify(dbOutbox).markSent(evt.id);
    }

    @Test
    @DisplayName("markOutboxSent ignores non-matching events")
    void markOutboxSent_nonMatching_ignored() throws Exception {
        OutboxEventRepository dbOutbox = spy(new OutboxEventRepository());
        dbOutbox.em = em;
        OutboxEventEntity evt = new OutboxEventEntity();
        evt.id = UUID.randomUUID();
        evt.aggregateId = UUID.randomUUID();
        doReturn(List.of(evt)).when(dbOutbox).findPending(100);
        setServiceField("outbox", dbOutbox);

        service.markOutboxSent(payment.id());

        verify(dbOutbox, never()).markSent(any());
    }

    @Test
    @DisplayName("markOutboxSent tolerates failures")
    void markOutboxSent_throws_tolerated() throws Exception {
        OutboxEventRepository dbOutbox = spy(new OutboxEventRepository());
        dbOutbox.em = em;
        doThrow(new RuntimeException("db down")).when(dbOutbox).findPending(100);
        setServiceField("outbox", dbOutbox);

        assertDoesNotThrow(() -> service.markOutboxSent(payment.id()));
    }

    @Test
    @DisplayName("markOutboxSent early-returns without outbox EM")
    void markOutboxSent_noEm_noOp() {
        assertDoesNotThrow(() -> service.markOutboxSent(payment.id()));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("markOutboxSent early-returns without outbox bean")
    void markOutboxSent_noOutbox_noOp() throws Exception {
        setServiceField("outbox", null);
        assertDoesNotThrow(() -> service.markOutboxSent(payment.id()));
    }

    @Test
    @DisplayName("persistWithOutbox skips outbox when bean missing")
    void persistWithOutbox_noOutbox_skips() throws Exception {
        setServiceField("outbox", null);
        when(repository.save(any(Payment.class), anyString())).thenReturn(payment);
        Payment saved = service.persistWithOutbox(payment, "key-no-outbox");
        assertEquals(payment.id(), saved.id());
    }

    @Test
    @DisplayName("findExistingPayment returns empty without outbox bean")
    void findExistingPayment_noOutbox_empty() throws Exception {
        setServiceField("outbox", null);
        when(repository.findByIdempotencyKey("key-no-outbox")).thenReturn(Optional.empty());
        assertTrue(service.findExistingPayment("key-no-outbox").isEmpty());
    }

    @Test
    @DisplayName("findExistingPayment hits cache on second call")
    void findExistingPayment_cacheHit() throws Exception {
        OutboxEventEntity evt = new OutboxEventEntity();
        evt.id = UUID.randomUUID();
        evt.aggregateId = payment.id();
        OutboxEventRepository spy = spy(outbox);
        doReturn(Optional.of(evt)).when(spy).findByIdempotencyKey("key-cache");
        setServiceField("outbox", spy);
        when(repository.findById(payment.id())).thenReturn(Optional.of(payment));

        assertTrue(service.findExistingPayment("key-cache").isPresent());
        assertTrue(service.findExistingPayment("key-cache").isPresent());
        verify(repository, times(1)).findById(payment.id());
    }

    @Test
    @DisplayName("findExistingPayment falls through when outbox payment missing")
    void findExistingPayment_outboxPaymentMissing_fallsThrough() throws Exception {
        OutboxEventEntity evt = new OutboxEventEntity();
        evt.id = UUID.randomUUID();
        evt.aggregateId = payment.id();
        OutboxEventRepository spy = spy(outbox);
        doReturn(Optional.of(evt)).when(spy).findByIdempotencyKey("key-miss-pay");
        setServiceField("outbox", spy);
        when(repository.findById(payment.id())).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey("key-miss-pay")).thenReturn(Optional.empty());

        assertTrue(service.findExistingPayment("key-miss-pay").isEmpty());
    }

    // ========== buildPayload fallback ==========

    @Test
    @DisplayName("createPayment falls back to minimal payload on serialization failure")
    void createPayment_serializationFails_minimalPayload() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new RuntimeException("mapper down"));
        setServiceField("objectMapper", failing);
        stubHappySave();

        assertNotNull(service.createPayment(request(), "key-serializer-fail"));
    }

    // ========== countPayments ==========

    @Test
    @DisplayName("countPayments delegates with parsed status")
    void countPayments_validStatus_delegates() {
        when(repository.count("cust-1", PaymentStatus.PENDING)).thenReturn(4L);
        assertEquals(4L, service.countPayments("cust-1", "PENDING"));
    }

    @Test
    @DisplayName("countPayments without filters delegates")
    void countPayments_noFilters_delegates() {
        when(repository.count(null, null)).thenReturn(9L);
        assertEquals(9L, service.countPayments(null, null));
    }

    @Test
    @DisplayName("countPayments rejects invalid status")
    void countPayments_invalidStatus_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.countPayments(null, "NOPE"));
        verify(repository, never()).count(any(), any());
    }

    @Test
    @DisplayName("findExistingPayment skips lookup without repository")
    void findExistingPayment_noRepository_empty() throws Exception {
        setServiceField("repository", null);
        assertTrue(service.findExistingPayment("key-no-repo").isEmpty());
    }

    @Test
    @DisplayName("buildPayload handles null amount and metadata")
    void buildPayload_nulls_defaults() {
        Payment p = new Payment(UUID.randomUUID(), null, "USD", "c", "M", null,
            PaymentStatus.PENDING, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        String payload = service.buildPayload(p);
        assertTrue(payload.contains("\"amount\":\"0\""));
        assertTrue(payload.contains("\"metadata\":{}"));
    }
}
