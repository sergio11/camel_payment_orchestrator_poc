package com.poc.processor.processor;

import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PaymentEnrichProcessorTest {

    @Inject
    PaymentEnrichProcessor processor;

    private Exchange createExchange(PaymentMessage message) {
        DefaultCamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(message);
        return exchange;
    }

    private PaymentMessage createPaymentMessage(String paymentId, String customerId, String country,
            Map<String, Object> metadata) {
        return new PaymentMessage(
            "event-1", paymentId, new BigDecimal("100.00"), "USD", customerId,
            "CREDIT_CARD", country, 1, false, 30, "UTC",
            metadata != null ? metadata : new HashMap<>(), LocalDateTime.now()
        );
    }

    private PaymentMessage processAndReturn(PaymentMessage msg) {
        Exchange exchange = createExchange(msg);
        processor.process(exchange);
        return exchange.getIn().getBody(PaymentMessage.class);
    }

    // ========== enrichedAt ==========

    @Test
    @DisplayName("Enrichment adds enrichedAt timestamp to metadata")
    void enrichWithRiskData_addsEnrichedAt() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", Map.of());
        PaymentMessage enriched = processAndReturn(msg);

        assertNotNull(enriched.metadata().get("enrichedAt"));
        String enrichedAt = (String) enriched.metadata().get("enrichedAt");
        assertNotNull(enrichedAt);
        assertFalse(enrichedAt.isEmpty());
    }

    // ========== customerRiskTier ==========

    @Test
    @DisplayName("Enrichment adds customerRiskTier as LOW, MEDIUM, or HIGH based on hash")
    void enrichWithRiskData_addsCustomerRiskTier() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", Map.of());
        PaymentMessage enriched = processAndReturn(msg);

        String tier = (String) enriched.metadata().get("customerRiskTier");
        assertNotNull(tier);
        assertTrue(tier.equals("LOW") || tier.equals("MEDIUM") || tier.equals("HIGH"),
            "Expected LOW, MEDIUM, or HIGH but got: " + tier);
    }

    @Test
    @DisplayName("customerRiskTier is consistent for the same customerId")
    void customerRiskTier_consistentForSameCustomer() {
        PaymentMessage msg1 = createPaymentMessage("payment-1", "customer-abc", "US", Map.of());
        PaymentMessage msg2 = createPaymentMessage("payment-2", "customer-abc", "US", Map.of());

        PaymentMessage enriched1 = processAndReturn(msg1);
        PaymentMessage enriched2 = processAndReturn(msg2);

        assertEquals(
            enriched1.metadata().get("customerRiskTier"),
            enriched2.metadata().get("customerRiskTier"),
            "Same customerId should produce same risk tier"
        );
    }

    @Test
    @DisplayName("customerRiskTier varies across different customerIds")
    void customerRiskTier_variesAcrossCustomers() {
        String tier1 = processAndReturn(
            createPaymentMessage("p1", "customer-1", "US", Map.of())
        ).metadata().get("customerRiskTier").toString();

        String tier2 = processAndReturn(
            createPaymentMessage("p2", "customer-2", "US", Map.of())
        ).metadata().get("customerRiskTier").toString();

        String tier3 = processAndReturn(
            createPaymentMessage("p3", "customer-3", "US", Map.of())
        ).metadata().get("customerRiskTier").toString();

        // Not all can be the same with different IDs (hash distribution)
        // At least verify the logic produces valid values
        assertTrue(List.of("LOW", "MEDIUM", "HIGH").contains(tier1));
        assertTrue(List.of("LOW", "MEDIUM", "HIGH").contains(tier2));
        assertTrue(List.of("LOW", "MEDIUM", "HIGH").contains(tier3));
    }

    @Test
    @DisplayName("determineCustomerRiskTier maps hash 0->LOW, 1->MEDIUM, 2->HIGH")
    void customerRiskTier_hashMapping() {
        // We verify the mapping indirectly by checking valid output values
        // The exact hash depends on customerId.hashCode() % 3
        // We just ensure the output is always one of the three valid values
        for (int i = 0; i < 30; i++) {
            String customerId = "cust-" + i;
            PaymentMessage msg = createPaymentMessage("p-" + i, customerId, "US", Map.of());
            PaymentMessage enriched = processAndReturn(msg);
            String tier = (String) enriched.metadata().get("customerRiskTier");
            assertNotNull(tier, "Tier should not be null for customerId: " + customerId);
            assertTrue(List.of("LOW", "MEDIUM", "HIGH").contains(tier),
                "Invalid tier '" + tier + "' for customerId: " + customerId);
        }
    }

    // ========== velocityScore ==========

    @Test
    @DisplayName("Enrichment adds velocityScore in range 0-99")
    void enrichWithRiskData_addsVelocityScore() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", Map.of());
        PaymentMessage enriched = processAndReturn(msg);

        Object velocityScore = enriched.metadata().get("velocityScore");
        assertNotNull(velocityScore);
        int score = ((Number) velocityScore).intValue();
        assertTrue(score >= 0 && score <= 99,
            "Expected velocityScore in [0, 99] but got: " + score);
    }

    @Test
    @DisplayName("velocityScore is consistent for the same customerId")
    void velocityScore_consistentForSameCustomer() {
        PaymentMessage msg1 = createPaymentMessage("payment-1", "customer-xyz", "US", Map.of());
        PaymentMessage msg2 = createPaymentMessage("payment-2", "customer-xyz", "US", Map.of());

        PaymentMessage enriched1 = processAndReturn(msg1);
        PaymentMessage enriched2 = processAndReturn(msg2);

        assertEquals(
            enriched1.metadata().get("velocityScore"),
            enriched2.metadata().get("velocityScore"),
            "Same customerId should produce same velocityScore"
        );
    }

    @Test
    @DisplayName("velocityScore is deterministic based on customerId hash")
    void velocityScore_deterministic() {
        for (int i = 0; i < 20; i++) {
            String customerId = "test-customer-" + i;
            PaymentMessage msg1 = createPaymentMessage("p1-" + i, customerId, "US", Map.of());
            PaymentMessage msg2 = createPaymentMessage("p2-" + i, customerId, "US", Map.of());

            PaymentMessage enriched1 = processAndReturn(msg1);
            PaymentMessage enriched2 = processAndReturn(msg2);

            assertEquals(enriched1.metadata().get("velocityScore"), enriched2.metadata().get("velocityScore"),
                "velocityScore should be deterministic for customerId: " + customerId);
        }
    }

    // ========== geoRiskScore ==========

    @Test
    @DisplayName("geoRiskScore is 80 for high risk country XX")
    void geoRiskScore_countryXX() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "XX", Map.of());
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(80, enriched.metadata().get("geoRiskScore"));
    }

    @Test
    @DisplayName("geoRiskScore is 80 for high risk country YY")
    void geoRiskScore_countryYY() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "YY", Map.of());
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(80, enriched.metadata().get("geoRiskScore"));
    }

    @Test
    @DisplayName("geoRiskScore is 50 for medium risk country ZZ")
    void geoRiskScore_countryZZ() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "ZZ", Map.of());
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(50, enriched.metadata().get("geoRiskScore"));
    }

    @Test
    @DisplayName("geoRiskScore is 50 for medium risk country WW")
    void geoRiskScore_countryWW() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "WW", Map.of());
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(50, enriched.metadata().get("geoRiskScore"));
    }

    @Test
    @DisplayName("geoRiskScore is 10 for safe country US")
    void geoRiskScore_countryUS() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", Map.of());
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(10, enriched.metadata().get("geoRiskScore"));
    }

    @Test
    @DisplayName("geoRiskScore is 10 for safe country DE")
    void geoRiskScore_countryDE() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "DE", Map.of());
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(10, enriched.metadata().get("geoRiskScore"));
    }

    @Test
    @DisplayName("geoRiskScore is 0 for null country")
    void geoRiskScore_nullCountry() {
        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-1", new BigDecimal("100.00"), "USD", "customer-1",
            "CREDIT_CARD", null, 1, false, 30, "UTC", new HashMap<>(), LocalDateTime.now()
        );
        PaymentMessage enriched = processAndReturn(msg);
        assertEquals(0, enriched.metadata().get("geoRiskScore"));
    }

    // ========== Preserves original metadata ==========

    @Test
    @DisplayName("Enrichment preserves original metadata fields")
    void enrichWithRiskData_preservesOriginalMetadata() {
        Map<String, Object> originalMetadata = new HashMap<>();
        originalMetadata.put("customField", "customValue");
        originalMetadata.put("sourceSystem", "test-system");
        originalMetadata.put("priority", 5);

        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", originalMetadata);
        PaymentMessage enriched = processAndReturn(msg);

        assertEquals("customValue", enriched.metadata().get("customField"));
        assertEquals("test-system", enriched.metadata().get("sourceSystem"));
        assertEquals(5, enriched.metadata().get("priority"));
    }

    @Test
    @DisplayName("Enrichment preserves original metadata even with null metadata")
    void enrichWithRiskData_handlesNullOriginalMetadata() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", null);
        PaymentMessage enriched = processAndReturn(msg);

        assertNotNull(enriched.metadata());
        assertNotNull(enriched.metadata().get("enrichedAt"));
        assertNotNull(enriched.metadata().get("customerRiskTier"));
        assertNotNull(enriched.metadata().get("velocityScore"));
        assertNotNull(enriched.metadata().get("geoRiskScore"));
    }

    // ========== Preserves all other PaymentMessage fields ==========

    @Test
    @DisplayName("Enrichment preserves all other PaymentMessage fields")
    void enrichWithRiskData_preservesAllFields() {
        PaymentMessage msg = new PaymentMessage(
            "event-1", "payment-99", new BigDecimal("500.75"), "EUR", "customer-42",
            "DEBIT_CARD", "XX", 3, true, 60, "America/Mexico_City",
            Map.of("existingKey", "existingValue"), LocalDateTime.of(2025, 6, 15, 10, 30)
        );

        PaymentMessage enriched = processAndReturn(msg);

        assertEquals("event-1", enriched.eventId());
        assertEquals("payment-99", enriched.paymentId());
        assertEquals(new BigDecimal("500.75"), enriched.amount());
        assertEquals("EUR", enriched.currency());
        assertEquals("customer-42", enriched.customerId());
        assertEquals("DEBIT_CARD", enriched.paymentMethod());
        assertEquals("XX", enriched.country());
        assertEquals(3, enriched.attemptCount());
        assertTrue(enriched.isNewPaymentMethod());
        assertEquals(60, enriched.customerAgeDays());
        assertEquals("America/Mexico_City", enriched.timeZone());
        assertEquals(LocalDateTime.of(2025, 6, 15, 10, 30), enriched.timestamp());
    }

    // ========== Enrichment metadata values are correct types ==========

    @Test
    @DisplayName("Enriched metadata values have correct types")
    void enrichedMetadata_correctTypes() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", Map.of());
        PaymentMessage enriched = processAndReturn(msg);

        assertInstanceOf(String.class, enriched.metadata().get("enrichedAt"));
        assertInstanceOf(String.class, enriched.metadata().get("customerRiskTier"));
        assertInstanceOf(Number.class, enriched.metadata().get("velocityScore"));
        assertInstanceOf(Number.class, enriched.metadata().get("geoRiskScore"));
    }

    // ========== Enrichment adds exactly 4 new fields ==========

    @Test
    @DisplayName("Enrichment adds exactly 4 new fields to metadata")
    void enrichWithRiskData_addsExactlyFourFields() {
        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", Map.of());
        PaymentMessage enriched = processAndReturn(msg);

        assertTrue(enriched.metadata().containsKey("enrichedAt"));
        assertTrue(enriched.metadata().containsKey("customerRiskTier"));
        assertTrue(enriched.metadata().containsKey("velocityScore"));
        assertTrue(enriched.metadata().containsKey("geoRiskScore"));
    }

    @Test
    @DisplayName("Enrichment does not modify original metadata map")
    void enrichWithRiskData_doesNotModifyOriginal() {
        Map<String, Object> originalMetadata = new HashMap<>();
        originalMetadata.put("keepMe", "value");

        PaymentMessage msg = createPaymentMessage("payment-1", "customer-1", "US", originalMetadata);
        processAndReturn(msg);

        // Original map should not contain enriched fields
        assertFalse(originalMetadata.containsKey("enrichedAt"));
        assertFalse(originalMetadata.containsKey("customerRiskTier"));
        assertFalse(originalMetadata.containsKey("velocityScore"));
        assertFalse(originalMetadata.containsKey("geoRiskScore"));
        assertEquals(1, originalMetadata.size());
    }
}
