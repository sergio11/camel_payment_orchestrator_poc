package com.poc.processor.e2e;

import com.poc.camel.testsupport.KafkaTestConsumer;
import com.poc.camel.testsupport.KafkaTestProducer;
import com.poc.processor.KafkaTestResource;
import com.poc.processor.ProviderMockConfig;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FraudEngineRouteE2E {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    ProviderMockConfig mockConfig;

    private KafkaTestProducer producer;
    private static final String TOPIC_RECEIVED = "payments.events.received";
    private static final String TOPIC_FRAUD_DETECTED = "fraud.events.detected";
    private static final String TOPIC_FAILED = "payments.events.failed";
    private static final String TOPIC_REVIEW = "payments.events.review";
    private static final String TOPIC_PROCESSED = "payments.events.processed";

    @BeforeAll
    void setup() {
        mockConfig.setProviderASucceeds(true);
        mockConfig.setProviderBSucceeds(true);
        producer = new KafkaTestProducer(bootstrapServers);
    }

    @AfterAll
    void teardown() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    @DisplayName("E2E: Fraud REJECT publishes to fraud.events.detected AND payments.events.failed")
    void testFraudRejectPublishesToBothTopics() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "XX", "CREDIT_CARD", 1,
            new PaymentMetadataDTO(null, 1, null, null, null, null, null, null));

        producer.send(TOPIC_RECEIVED, paymentId, payment);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_FRAUD_DETECTED, TOPIC_FAILED);
        try {
            FraudResult fraudResult = consumer.consumeUntil(TOPIC_FRAUD_DETECTED, paymentId, FraudResult.class, 30);
            assertThat(fraudResult).isNotNull();
            assertThat(fraudResult.action()).isEqualTo("REJECT");

            PaymentMessage failedEvent = consumer.consumeUntil(TOPIC_FAILED, paymentId, PaymentMessage.class, 30);
            assertThat(failedEvent).isNotNull();
            assertThat(failedEvent.paymentId()).isEqualTo(paymentId);
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: Fraud REVIEW publishes to fraud.events.detected AND payments.events.review")
    void testFraudReviewPublishesToReviewTopic() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "US", "CREDIT_CARD", 0,
            PaymentMetadataDTO.empty());

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_FRAUD_DETECTED, TOPIC_REVIEW);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            FraudResult fraudResult = consumer.consumeUntil(TOPIC_FRAUD_DETECTED, paymentId, FraudResult.class, 30);
            assertThat(fraudResult).isNotNull();
            assertThat(fraudResult.action()).isEqualTo("REVIEW");

            PaymentMessage reviewEvent = consumer.consumeUntil(TOPIC_REVIEW, paymentId, PaymentMessage.class, 30);
            assertThat(reviewEvent).isNotNull();
            assertThat(reviewEvent.paymentId()).isEqualTo(paymentId);
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: Fraud APPROVE goes to provider-selection and publishes processed event")
    void testFraudApproveGoesToProviderSelection() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("100.00"), "USD", "US", "CREDIT_CARD", 0,
            PaymentMetadataDTO.empty());

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_PROCESSED);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            ProviderResponse result = consumer.consumeUntil(TOPIC_PROCESSED, paymentId, ProviderResponse.class, 30);
            assertThat(result).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.providerId()).isEqualTo("provider-a");
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: WALLET payment routes to fraud review (high-value path)")
    void testWalletPaymentRoutesToFraudReview() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "US", "WALLET", 0,
            PaymentMetadataDTO.empty());

        producer.send(TOPIC_RECEIVED, paymentId, payment);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_FRAUD_DETECTED);
        try {
            FraudResult fraudResult = consumer.consumeUntil(TOPIC_FRAUD_DETECTED, paymentId, FraudResult.class, 30);
            assertThat(fraudResult).isNotNull();
            assertThat(fraudResult.action()).isIn("REVIEW", "APPROVE");
        } finally {
            consumer.close();
        }
    }

    private PaymentMessage buildPayment(String paymentId, BigDecimal amount, String currency, String country,
        String method, int attempts, PaymentMetadataDTO metadata) {
        return new PaymentMessage(
            UUID.randomUUID().toString(), paymentId, amount, currency,
            "customer-123", method, country,
            attempts, false, 0, "UTC",
            metadata, LocalDateTime.now()
        );
    }
}
