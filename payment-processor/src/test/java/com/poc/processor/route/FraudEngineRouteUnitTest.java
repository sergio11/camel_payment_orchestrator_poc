package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class FraudEngineRouteUnitTest {

    private CamelContext context;
    private ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        Properties props = new Properties();
        props.setProperty("kafka.topic.fraud.detected", "fraud.events.detected");
        props.setProperty("kafka.topic.payments.failed", "payments.events.failed");
        props.setProperty("kafka.topic.payments.review", "payments.events.review");
        context.getPropertiesComponent().setOverrideProperties(props);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        FraudEngineRoute route = new FraudEngineRoute();
        setField(route, "objectMapper", objectMapper);

        context.addRoutes(route);

        // Replace the kafka endpoints at the route-definition level instead of
        // interceptSendToEndpoint: deterministic, no Kafka producers are created
        // (no localhost:9092 connection storms) and both kafka sends per route
        // land on the same mock.
        AdviceWith.adviceWith(context, "fraud-reject", a -> {
            a.replaceFromWith("direct:test-fraud-reject");
            a.weaveByToString("To\\[kafka:.*\\]").replace().to("mock:kafka-fraud-detected");
        });
        AdviceWith.adviceWith(context, "fraud-review-queue", a -> {
            a.replaceFromWith("direct:test-fraud-review");
            a.weaveByToString("To\\[kafka:.*\\]").replace().to("mock:kafka-fraud-review");
        });

        context.start();
        producerTemplate = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (producerTemplate != null) producerTemplate.stop();
        if (context != null) context.stop();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private PaymentMessage createMessage(String paymentId) {
        return new PaymentMessage(
            "evt-1", paymentId, new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("fraud-reject route should be registered")
    void testFraudRejectRouteRegistered() {
        assertNotNull(context.getRoute("fraud-reject"));
    }

    @Test
    @DisplayName("fraud-review-queue route should be registered")
    void testFraudReviewQueueRouteRegistered() {
        assertNotNull(context.getRoute("fraud-review-queue"));
    }

    @Test
    @DisplayName("fraud-reject creates FraudResult when evaluation is null")
    void testFraudReject_nullEvaluation() throws Exception {
        MockEndpoint mockFraudDetected = context.getEndpoint("mock:kafka-fraud-detected", MockEndpoint.class);
        mockFraudDetected.reset();
        mockFraudDetected.expectedMinimumMessageCount(1);

        PaymentMessage msg = createMessage("pay-reject-null");
        producerTemplate.sendBodyAndHeader("direct:test-fraud-reject", msg,
            "OriginalPaymentMessage", msg);

        mockFraudDetected.assertIsSatisfied();
    }

    @Test
    @DisplayName("fraud-reject uses provided FraudEvaluation")
    void testFraudReject_withEvaluation() throws Exception {
        MockEndpoint mockFraudDetected = context.getEndpoint("mock:kafka-fraud-detected", MockEndpoint.class);
        mockFraudDetected.reset();
        mockFraudDetected.expectedMinimumMessageCount(1);

        PaymentMessage msg = createMessage("pay-reject-eval");
        FraudEvaluation eval = new FraudEvaluation(
            "pay-reject-eval", msg.amount(), msg.customerId(),
            80, FraudAction.REJECT, "Test", List.of("TEST")
        );

        // Production contract: FraudEvaluation travels as exchange PROPERTY
        // (PaymentProcessorRoute.setProperty), OriginalPaymentMessage as header.
        producerTemplate.send("direct:test-fraud-reject", exchange -> {
            exchange.getIn().setBody(msg);
            exchange.getIn().setHeader("OriginalPaymentMessage", msg);
            exchange.setProperty("FraudEvaluation", eval);
        });

        mockFraudDetected.assertIsSatisfied();
    }

    @Test
    @DisplayName("fraud-review-queue creates FraudResult when evaluation is null")
    void testFraudReviewQueue_nullEvaluation() throws Exception {
        MockEndpoint mockFraudReview = context.getEndpoint("mock:kafka-fraud-review", MockEndpoint.class);
        mockFraudReview.reset();
        mockFraudReview.expectedMinimumMessageCount(1);

        PaymentMessage msg = createMessage("pay-review-null");
        producerTemplate.sendBodyAndHeader("direct:test-fraud-review", msg,
            "OriginalPaymentMessage", msg);

        mockFraudReview.assertIsSatisfied();
    }

    @Test
    @DisplayName("fraud-review-queue uses provided FraudEvaluation")
    void testFraudReviewQueue_withEvaluation() throws Exception {
        MockEndpoint mockFraudReview = context.getEndpoint("mock:kafka-fraud-review", MockEndpoint.class);
        mockFraudReview.reset();
        mockFraudReview.expectedMinimumMessageCount(1);

        PaymentMessage msg = createMessage("pay-review-eval");
        FraudEvaluation eval = new FraudEvaluation(
            "pay-review-eval", msg.amount(), msg.customerId(),
            60, FraudAction.REVIEW, "Review", List.of("REVIEW")
        );

        // Production contract: FraudEvaluation travels as exchange PROPERTY
        // (PaymentProcessorRoute.setProperty), OriginalPaymentMessage as header.
        producerTemplate.send("direct:test-fraud-review", exchange -> {
            exchange.getIn().setBody(msg);
            exchange.getIn().setHeader("OriginalPaymentMessage", msg);
            exchange.setProperty("FraudEvaluation", eval);
        });

        mockFraudReview.assertIsSatisfied();
    }
}
