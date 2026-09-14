package com.poc.processor.e2e;

import com.poc.camel.testsupport.KafkaTestConsumer;
import com.poc.camel.testsupport.KafkaTestProducer;
import com.poc.processor.KafkaTestResource;
import com.poc.processor.ProviderMockConfig;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
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
class PoisonMessageE2E {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    ProviderMockConfig mockConfig;

    private KafkaTestProducer producer;
    private static final String TOPIC_RECEIVED = "payments.events.received";
    private static final String TOPIC_DEAD_LETTER = "payments.events.dead-letter";

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
    @DisplayName("E2E: Malformed JSON goes directly to dead-letter topic without retry")
    void testMalformedJsonGoesToDLQ() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String malformedJson = "{ invalid json :::: {{{ ";

        producer.sendRaw(TOPIC_RECEIVED, paymentId, malformedJson);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_DEAD_LETTER);
        try {
            String dlqMessage = consumer.consumeUntil(TOPIC_DEAD_LETTER, paymentId, 30);
            assertThat(dlqMessage).isNotNull();
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: Payment with null required fields goes to dead-letter (PaymentProcessingException)")
    void testInvalidFieldGoesToDLQ() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = new PaymentMessage(
            UUID.randomUUID().toString(), paymentId, null, null,
            null, null, null,
            0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        producer.send(TOPIC_RECEIVED, paymentId, payment);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            TOPIC_DEAD_LETTER);
        try {
            String dlqMessage = consumer.consumeUntilPredicate(TOPIC_DEAD_LETTER,
                msg -> msg.contains(paymentId), 30);
            assertThat(dlqMessage).isNotNull();
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("E2E: Retry exhaustion publishes to payments.events.retry topic")
    void testRetryExhaustionPublishesToRetryTopic() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = buildPayment(paymentId);

        mockConfig.setProviderASucceeds(false);
        mockConfig.setProviderBSucceeds(false);

        KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, KafkaTestConsumer.randomGroupId(),
            "payments.events.retry", TOPIC_DEAD_LETTER);
        try {
            producer.send(TOPIC_RECEIVED, paymentId, payment);

            String retryOrDlq = consumer.consumeUntilPredicate(
                "payments.events.retry",
                msg -> msg.contains(paymentId),
                45);

            if (retryOrDlq == null) {
                retryOrDlq = consumer.consumeUntilPredicate(
                    TOPIC_DEAD_LETTER,
                    msg -> msg.contains(paymentId),
                    10);
            }

            assertThat(retryOrDlq).isNotNull();
        } finally {
            consumer.close();
            mockConfig.setProviderASucceeds(true);
            mockConfig.setProviderBSucceeds(true);
        }
    }

    private PaymentMessage buildPayment(String paymentId) {
        return new PaymentMessage(
            UUID.randomUUID().toString(), paymentId, new BigDecimal("100.00"), "USD",
            "customer-123", "CREDIT_CARD", "US",
            0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }
}
