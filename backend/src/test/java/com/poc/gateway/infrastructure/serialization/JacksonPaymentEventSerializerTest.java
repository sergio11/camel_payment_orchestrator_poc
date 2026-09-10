package com.poc.gateway.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JacksonPaymentEventSerializerTest {

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    JacksonPaymentEventSerializer serializer;

    private Payment buildPayment(PaymentMetadata metadata) {
        return new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, metadata, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void serialize_success_returnsFullJson() throws Exception {
        Payment payment = buildPayment(PaymentMetadata.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":\"ok\"}");

        String json = serializer.serialize(payment);

        assertEquals("{\"paymentId\":\"ok\"}", json);
        verify(objectMapper).writeValueAsString(any(Map.class));
    }

    @Test
    void serialize_withNonNullMetadata_usesMetadata() throws Exception {
        PaymentMetadata meta = new PaymentMetadata("order-1", 3, true, 30, "LOW", null, null, null);
        Payment payment = buildPayment(meta);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":\"ok\"}");

        serializer.serialize(payment);

        verify(objectMapper).writeValueAsString(argThat(arg -> {
            Map<?, ?> map = (Map<?, ?>) arg;
            return map.get("metadata") == meta;
        }));
    }

    @Test
    void serialize_withNullMetadata_usesEmptyMap() throws Exception {
        Payment payment = buildPayment(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":\"ok\"}");

        serializer.serialize(payment);

        verify(objectMapper).writeValueAsString(argThat(arg -> {
            Map<?, ?> map = (Map<?, ?>) arg;
            Object metadataValue = map.get("metadata");
            return metadataValue instanceof Map && ((Map<?, ?>) metadataValue).isEmpty();
        }));
    }

    @Test
    void serialize_withNullAmount_usesZero() throws Exception {
        Payment payment = new Payment(
            UUID.randomUUID(), null, "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":\"ok\"}");

        serializer.serialize(payment);

        verify(objectMapper).writeValueAsString(argThat(arg -> {
            Map<?, ?> map = (Map<?, ?>) arg;
            return "0".equals(map.get("amount"));
        }));
    }

    @Test
    void serialize_withNonNullAmount_usesAmountString() throws Exception {
        Payment payment = buildPayment(PaymentMetadata.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":\"ok\"}");

        serializer.serialize(payment);

        verify(objectMapper).writeValueAsString(argThat(arg -> {
            Map<?, ?> map = (Map<?, ?>) arg;
            return "100.00".equals(map.get("amount"));
        }));
    }

    @Test
    void serialize_serializerException_returnsMinimalJson() throws Exception {
        Payment payment = buildPayment(PaymentMetadata.empty());
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialize error"));

        String json = serializer.serialize(payment);

        assertTrue(json.contains("\"paymentId\""));
        assertTrue(json.contains(payment.id().toString()));
        assertFalse(json.contains("amount"));
    }

    @Test
    void serialize_serializerException_returnsValidMinimalPayload() throws Exception {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(
            paymentId, new BigDecimal("50"), "EUR", "cust-2",
            "BANK_TRANSFER", "DE", PaymentStatus.APPROVED,
            "Stripe", null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("fail"));

        String json = serializer.serialize(payment);

        assertEquals("{\"paymentId\":\"" + paymentId + "\"}", json);
    }

    @Test
    void serialize_mapsAllFieldsCorrectly() throws Exception {
        Payment payment = buildPayment(PaymentMetadata.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        serializer.serialize(payment);

        verify(objectMapper).writeValueAsString(argThat(arg -> {
            Map<?, ?> map = (Map<?, ?>) arg;
            return map.containsKey("paymentId")
                && map.containsKey("amount")
                && map.containsKey("currency")
                && map.containsKey("customerId")
                && map.containsKey("paymentMethod")
                && map.containsKey("country")
                && map.containsKey("metadata");
        }));
    }
}
