package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.processor.application.FraudRoutingService;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
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
    FraudRoutingService fraudRoutingService;

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
            .thenReturn(new FraudRoutingService.FraudRoutingDecision(FraudAction.APPROVE, CamelRouteConstants.DIRECT_FRAUD_CHECK, eval));

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
}
