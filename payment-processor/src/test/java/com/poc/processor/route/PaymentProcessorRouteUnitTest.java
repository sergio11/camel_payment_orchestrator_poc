package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.processor.port.inbound.RouteFraudUseCase;
import com.poc.processor.port.inbound.RouteFraudUseCase.FraudRoutingDecision;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.exception.InvalidPaymentException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorRouteUnitTest {

    @Mock
    RouteFraudUseCase fraudRoutingService;

    @Mock
    EnrichPaymentUseCase enrichPaymentUseCase;

    @Mock
    EvaluateFraudUseCase evaluateFraudUseCase;

    private CamelContext context;
    private ProducerTemplate producerTemplate;

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

    @Test
    @DisplayName("restorePaymentIdFromKafkaKey preserves existing OriginalPaymentId")
    void testRestorePaymentIdFromKafkaKey_existing() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, "pid-1");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, "key-1");
        PaymentProcessorRoute.restorePaymentIdFromKafkaKey(exchange);
        assertEquals("pid-1", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
    }

    @Test
    @DisplayName("restorePaymentIdFromKafkaKey returns null when no headers")
    void testRestorePaymentIdFromKafkaKey_noHeaders() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        PaymentProcessorRoute.restorePaymentIdFromKafkaKey(exchange);
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
    }

    @Test
    @DisplayName("restorePaymentIdFromKafkaKey uses kafka.KEY when OriginalPaymentId absent")
    void testRestorePaymentIdFromKafkaKey_usesKafkaKey() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, "key-3");
        PaymentProcessorRoute.restorePaymentIdFromKafkaKey(exchange);
        assertEquals("key-3", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
    }

    @Test
    @DisplayName("payment-processor route should configure all sub-routes")
    void testOnExceptionConfigured() throws Exception {
        context = new DefaultCamelContext();
        Properties props = new Properties();
        props.setProperty("kafka.topic.payments.received", "payments.events.received");
        props.setProperty("kafka.topic.payments.processed", "payments.events.processed");
        props.setProperty("kafka.topic.payments.failed", "payments.events.failed");
        props.setProperty("kafka.topic.payments.retry", "payments.events.retry");
        props.setProperty("kafka.topic.payments.review", "payments.events.review");
        props.setProperty("kafka.topic.dead.letter", "payments.events.dead-letter");
        props.setProperty("kafka.topic.fraud.detected", "fraud.events.detected");
        props.setProperty("camel.route.payment-processor.auto-startup", "false");
        props.setProperty("camel.route.retry-consumer.auto-startup", "false");
        context.getPropertiesComponent().setOverrideProperties(props);

        KafkaComponent kafka = new KafkaComponent();
        KafkaConfiguration kafkaConfig = new KafkaConfiguration();
        kafkaConfig.setBrokers("localhost:9092");
        kafka.setConfiguration(kafkaConfig);
        context.addComponent("kafka", kafka);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lenient().when(enrichPaymentUseCase.enrich(any(PaymentMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        FraudEvaluation eval = new FraudEvaluation("pay-1", BigDecimal.TEN, "cust", 10, FraudAction.APPROVE, "Low", List.of());
        lenient().when(evaluateFraudUseCase.evaluate(any(PaymentMessage.class))).thenReturn(eval);
        lenient().when(fraudRoutingService.route(any(PaymentMessage.class), any(FraudEvaluation.class)))
            .thenReturn(new FraudRoutingDecision(FraudAction.APPROVE, CamelRouteConstants.DIRECT_FRAUD_CHECK, eval));

        PaymentProcessorRoute route = new PaymentProcessorRoute();
        setField(route, "objectMapper", objectMapper);
        setField(route, "fraudRoutingService", fraudRoutingService);
        setField(route, "enrichPaymentUseCase", enrichPaymentUseCase);
        setField(route, "evaluateFraudUseCase", evaluateFraudUseCase);

        context.addRoutes(route);
        context.start();
        producerTemplate = context.createProducerTemplate();

        assertNotNull(context.getRoute("payment-processor"));
        assertNotNull(context.getRoute("fraud-check-standard"));
        assertNotNull(context.getRoute("fraud-review-high-value"));
        assertNotNull(context.getRoute("retry-handler"));
        assertNotNull(context.getRoute("poison-dlq-handler"));
        assertNotNull(context.getRoute("error-dlq-handler"));
    }

    private PaymentMessage validMessage(String paymentId, String eventId) {
        return new PaymentMessage(
            eventId, paymentId, BigDecimal.TEN, "USD", "cust-1",
            "CREDIT_CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    private PaymentProcessorRoute newRoute() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        PaymentProcessorRoute route = new PaymentProcessorRoute();
        setField(route, "objectMapper", objectMapper);
        setField(route, "fraudRoutingService", fraudRoutingService);
        setField(route, "enrichPaymentUseCase", enrichPaymentUseCase);
        setField(route, "evaluateFraudUseCase", evaluateFraudUseCase);
        return route;
    }

    private Exchange exchangeWithBody(Object body) {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    @DisplayName("validateAndInitHeaders sets payment and event headers")
    void testValidateAndInitHeaders_setsHeaders() {
        Exchange exchange = exchangeWithBody(validMessage("pay-10", "evt-10"));
        PaymentProcessorRoute.validateAndInitHeaders(exchange);
        assertEquals("pay-10", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("evt-10", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("validateAndInitHeaders keeps pre-existing headers")
    void testValidateAndInitHeaders_keepsExisting() {
        Exchange exchange = exchangeWithBody(validMessage("pay-11", "evt-11"));
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, "kept-pay");
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, "kept-evt");
        PaymentProcessorRoute.validateAndInitHeaders(exchange);
        assertEquals("kept-pay", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("kept-evt", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("validateAndInitHeaders rejects invalid payment")
    void testValidateAndInitHeaders_invalid() {
        Exchange exchange = exchangeWithBody(validMessage(null, "evt-12"));
        assertThrows(InvalidPaymentException.class,
            () -> PaymentProcessorRoute.validateAndInitHeaders(exchange));
    }

    @Test
    @DisplayName("enrichPayment swaps body with enriched message")
    void testEnrichPayment_swapsBody() throws Exception {
        PaymentMessage msg = validMessage("pay-20", "evt-20");
        PaymentMessage enriched = validMessage("pay-20", "evt-20");
        when(enrichPaymentUseCase.enrich(msg)).thenReturn(enriched);
        Exchange exchange = exchangeWithBody(msg);
        newRoute().enrichPayment(exchange);
        assertSame(enriched, exchange.getIn().getBody());
        verify(enrichPaymentUseCase).enrich(msg);
    }

    @Test
    @DisplayName("evaluateAndRoute sets fraud headers and evaluation property")
    void testEvaluateAndRoute_setsHeaders() throws Exception {
        PaymentMessage msg = validMessage("pay-30", "evt-30");
        FraudEvaluation eval = new FraudEvaluation(
            "pay-30", BigDecimal.TEN, "cust-1", 10, FraudAction.APPROVE, "Low", List.of());
        when(evaluateFraudUseCase.evaluate(msg)).thenReturn(eval);
        when(fraudRoutingService.route(msg, eval)).thenReturn(
            new FraudRoutingDecision(FraudAction.APPROVE, CamelRouteConstants.DIRECT_FRAUD_CHECK, eval));
        Exchange exchange = exchangeWithBody(msg);
        newRoute().evaluateAndRoute(exchange);
        assertSame(msg, exchange.getIn().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE));
        assertSame(eval, exchange.getProperty("FraudEvaluation"));
        assertEquals("APPROVE", exchange.getIn().getHeader(CamelRouteConstants.HEADER_FRAUD_ACTION));
        assertEquals(10, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RISK_SCORE));
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK,
            exchange.getIn().getHeader(CamelRouteConstants.HEADER_FRAUD_ROUTE_TARGET));
        verify(evaluateFraudUseCase).evaluate(msg);
        verify(fraudRoutingService).route(msg, eval);
    }

    @Test
    @DisplayName("incrementRetryCount handles Integer header")
    void testIncrementRetryCount_integer() {
        Exchange exchange = exchangeWithBody("x");
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_RETRY_COUNT, 2);
        PaymentProcessorRoute.incrementRetryCount(exchange);
        assertEquals(3, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT));
    }

    @Test
    @DisplayName("incrementRetryCount handles Long header")
    void testIncrementRetryCount_long() {
        Exchange exchange = exchangeWithBody("x");
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_RETRY_COUNT, 5L);
        PaymentProcessorRoute.incrementRetryCount(exchange);
        assertEquals(6, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT));
    }

    @Test
    @DisplayName("incrementRetryCount handles byte[] header")
    void testIncrementRetryCount_bytes() {
        Exchange exchange = exchangeWithBody("x");
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_RETRY_COUNT, "4".getBytes());
        PaymentProcessorRoute.incrementRetryCount(exchange);
        assertEquals(5, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT));
    }

    @Test
    @DisplayName("incrementRetryCount handles String header")
    void testIncrementRetryCount_string() {
        Exchange exchange = exchangeWithBody("x");
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_RETRY_COUNT, "7");
        PaymentProcessorRoute.incrementRetryCount(exchange);
        assertEquals(8, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT));
    }

    @Test
    @DisplayName("incrementRetryCount starts at 1 without header")
    void testIncrementRetryCount_missing() {
        Exchange exchange = exchangeWithBody("x");
        PaymentProcessorRoute.incrementRetryCount(exchange);
        assertEquals(1, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT));
    }

    @Test
    @DisplayName("incrementRetryCount starts at 1 with unparsable header")
    void testIncrementRetryCount_garbage() {
        Exchange exchange = exchangeWithBody("x");
        exchange.getIn().setHeader(CamelRouteConstants.HEADER_RETRY_COUNT, "abc");
        PaymentProcessorRoute.incrementRetryCount(exchange);
        assertEquals(1, exchange.getIn().getHeader(CamelRouteConstants.HEADER_RETRY_COUNT));
    }
}
