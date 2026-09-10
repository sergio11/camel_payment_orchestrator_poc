package com.poc.processor.route;

import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FraudEngineRouteTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @EndpointInject("mock:kafka-fraud-detected")
    MockEndpoint kafkaFraudDetected;

    @EndpointInject("mock:kafka-payments-failed")
    MockEndpoint kafkaPaymentsFailed;

    @EndpointInject("mock:kafka-payments-review")
    MockEndpoint kafkaPaymentsReview;

    private PaymentMessage createPaymentMessage() {
        return new PaymentMessage(
            "evt-1", "pay-null-test", new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    private void mockKafkaEndpoints() throws Exception {
        AdviceWith.adviceWith(camelContext, "fraud-reject", a -> {
            a.replaceFromWith("direct:test-fraud-reject");
            a.interceptSendToEndpoint("kafka:*")
                .skipSendToOriginalEndpoint()
                .to("mock:kafka-fraud-detected");
        });
        AdviceWith.adviceWith(camelContext, "fraud-review-queue", a -> {
            a.replaceFromWith("direct:test-fraud-review");
            a.interceptSendToEndpoint("kafka:*")
                .skipSendToOriginalEndpoint()
                .to("mock:kafka-fraud-detected");
        });
    }

    @Test
    @DisplayName("fraud-reject handles null FraudEvaluation gracefully (fallback path)")
    void fraudReject_nullEvaluation_fallback() throws Exception {
        mockKafkaEndpoints();

        kafkaFraudDetected.reset();

        PaymentMessage msg = createPaymentMessage();
        producerTemplate.sendBodyAndHeader("direct:test-fraud-reject", msg,
            "OriginalPaymentMessage", msg);

        kafkaFraudDetected.expectedMinimumMessageCount(1);
        MockEndpoint.assertIsSatisfied(kafkaFraudDetected);
    }

    @Test
    @DisplayName("fraud-review-queue handles null FraudEvaluation gracefully (fallback path)")
    void fraudReviewQueue_nullEvaluation_fallback() throws Exception {
        mockKafkaEndpoints();

        kafkaFraudDetected.reset();

        PaymentMessage msg = createPaymentMessage();
        producerTemplate.sendBodyAndHeader("direct:test-fraud-review", msg,
            "OriginalPaymentMessage", msg);

        kafkaFraudDetected.expectedMinimumMessageCount(1);
        MockEndpoint.assertIsSatisfied(kafkaFraudDetected);
    }

    @Test
    @DisplayName("fraud-reject uses provided FraudEvaluation when property is set")
    void fraudReject_withEvaluation_usesProvided() throws Exception {
        AdviceWith.adviceWith(camelContext, "fraud-reject", a -> {
            a.replaceFromWith("direct:test-fraud-reject-eval");
            a.interceptSendToEndpoint("kafka:*")
                .skipSendToOriginalEndpoint()
                .to("mock:kafka-fraud-detected");
        });

        kafkaFraudDetected.reset();

        PaymentMessage msg = createPaymentMessage();
        FraudEvaluation eval = new FraudEvaluation(
            "pay-null-test", msg.amount(), msg.customerId(),
            80, FraudEvaluation.ACTION_REJECT, "Test reason", List.of("TEST_RULE")
        );

        producerTemplate.sendBodyAndHeaders("direct:test-fraud-reject-eval", msg,
            Map.of(
                "FraudEvaluation", eval,
                "OriginalPaymentMessage", msg
            ));

        kafkaFraudDetected.expectedMinimumMessageCount(1);
        MockEndpoint.assertIsSatisfied(kafkaFraudDetected);
    }

    @Test
    @DisplayName("fraud-review-queue uses provided FraudEvaluation when property is set")
    void fraudReviewQueue_withEvaluation_usesProvided() throws Exception {
        AdviceWith.adviceWith(camelContext, "fraud-review-queue", a -> {
            a.replaceFromWith("direct:test-fraud-review-eval");
            a.interceptSendToEndpoint("kafka:*")
                .skipSendToOriginalEndpoint()
                .to("mock:kafka-fraud-detected");
        });

        kafkaFraudDetected.reset();

        PaymentMessage msg = createPaymentMessage();
        FraudEvaluation eval = new FraudEvaluation(
            "pay-null-test", msg.amount(), msg.customerId(),
            60, FraudEvaluation.ACTION_REVIEW, "Test review", List.of("TEST_RULE")
        );

        producerTemplate.sendBodyAndHeaders("direct:test-fraud-review-eval", msg,
            Map.of(
                "FraudEvaluation", eval,
                "OriginalPaymentMessage", msg
            ));

        kafkaFraudDetected.expectedMinimumMessageCount(1);
        MockEndpoint.assertIsSatisfied(kafkaFraudDetected);
    }
}
