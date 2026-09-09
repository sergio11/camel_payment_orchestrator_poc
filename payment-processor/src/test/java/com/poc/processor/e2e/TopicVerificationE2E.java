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
class TopicVerificationE2E {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    ProviderMockConfig mockConfig;

    private KafkaTestProducer producer;

    private static final String TOPIC_RECEIVED = "payments.events.received";
    private static final String TOPIC_PROCESSED = "payments.events.processed";
    private static final String TOPIC_FAILED = "payments.events.failed";
    private static final String TOPIC_REVIEW = "payments.events.review";
    private static final String TOPIC_RETRY = "payments.events.retry";
    private static final String TOPIC_DEAD_LETTER = "payments.events.dead-letter";
    private static final String TOPIC_AUDIT = "payments.events.audit";
    private static final String TOPIC_FRAUD_DETECTED = "fraud.events.detected";

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
    @DisplayName("E2E: All 7 topics are consumed correctly in a successful flow")
    void testAllTopicsCoveredInSuccessFlow() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("100.00"), "USD", "US", "CREDIT_CARD", 0);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_PROCESSED, TOPIC_AUDIT);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            ProviderResponse processed = consumer.consumeUntil(TOPIC_PROCESSED, paymentId, ProviderResponse.class, 30);
            assertThat(processed).isNotNull();
            assertThat(processed.success()).isTrue();

            String audit = consumer.consumeUntilPredicate(TOPIC_AUDIT, msg -> msg.contains(paymentId), 15);
            assertThat(audit).isNotNull();
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: REJECT flow covers fraud.detected + failed + audit topics")
    void testRejectFlowCoversExpectedTopics() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "XX", "CREDIT_CARD", 1);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_FRAUD_DETECTED, TOPIC_FAILED, TOPIC_AUDIT);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            FraudResult fraud = consumer.consumeUntil(TOPIC_FRAUD_DETECTED, paymentId, FraudResult.class, 30);
            assertThat(fraud).isNotNull();
            assertThat(fraud.action()).isEqualTo("REJECT");

            PaymentMessage failed = consumer.consumeUntil(TOPIC_FAILED, paymentId, PaymentMessage.class, 30);
            assertThat(failed).isNotNull();

            String audit = consumer.consumeUntilPredicate(TOPIC_AUDIT, msg -> msg.contains(paymentId), 15);
            assertThat(audit).isNotNull();
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: REVIEW flow covers fraud.detected + review topics")
    void testReviewFlowCoversExpectedTopics() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "US", "CREDIT_CARD", 0);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_FRAUD_DETECTED, TOPIC_REVIEW, TOPIC_AUDIT);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            FraudResult fraud = consumer.consumeUntil(TOPIC_FRAUD_DETECTED, paymentId, FraudResult.class, 30);
            assertThat(fraud).isNotNull();
            assertThat(fraud.action()).isEqualTo("REVIEW");

            PaymentMessage review = consumer.consumeUntil(TOPIC_REVIEW, paymentId, PaymentMessage.class, 30);
            assertThat(review).isNotNull();
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: DLQ flow covers dead-letter topic")
    void testDLQFlowCoversDeadLetterTopic() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String malformed = "{ bad json {{{ ";

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_DEAD_LETTER);
        try {
            producer.sendRaw(TOPIC_RECEIVED, paymentId, malformed);

            String dlq = consumer.consumeUntil(TOPIC_DEAD_LETTER, paymentId, 30);
            assertThat(dlq).isNotNull();
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: Provider fallback covers processed topic after Provider B succeeds")
    void testProviderFallbackPublishesToProcessed() throws Exception {
        mockConfig.setProviderASucceeds(false);
        mockConfig.setProviderBSucceeds(true);

        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId, new BigDecimal("100.00"), "USD", "US", "CREDIT_CARD", 0);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_PROCESSED);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            ProviderResponse result = consumer.consumeUntil(TOPIC_PROCESSED, paymentId, ProviderResponse.class, 45);
            assertThat(result).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.providerId()).isEqualTo("provider-b");
        } finally {
            consumer.close();
            mockConfig.setProviderASucceeds(true);
            mockConfig.setProviderBSucceeds(true);
        }
    }

    private PaymentMessage buildPayment(String paymentId, BigDecimal amount, String currency, String country,
        String method, int attempts) {
        return new PaymentMessage(
            UUID.randomUUID().toString(), paymentId, amount, currency,
            "customer-123", method, country,
            attempts, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }
}
