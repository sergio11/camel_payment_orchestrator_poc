package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.processor.config.ProviderConfig;
import com.poc.processor.processor.ProviderRouterBean;
import com.poc.shared.event.ProviderResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderSelectionRouteUnitTest {

    @Mock
    ProviderConfig providerConfig;

    private CamelContext context;
    private ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() {
        lenient().when(providerConfig.primaryProvider()).thenReturn("provider-a");
        lenient().when(providerConfig.circuitBreakerFailureThreshold()).thenReturn(50);
        lenient().when(providerConfig.circuitBreakerWaitDuration()).thenReturn(Duration.ofSeconds(5));
        lenient().when(providerConfig.circuitBreakerSlidingWindowSize()).thenReturn(100);
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

    @Test
    @DisplayName("ProviderSelectionRoute should configure all routes")
    void testConfigureRoutes() throws Exception {
        context = new DefaultCamelContext();
        Properties props = new Properties();
        props.setProperty("kafka.topic.payments.processed", "payments.events.processed");
        props.setProperty("kafka.topic.payments.review", "payments.events.review");
        props.setProperty("kafka.topic.payments.failed", "payments.events.failed");
        props.setProperty("kafka.topic.dead.letter", "payments.events.dead-letter");
        props.setProperty("kafka.topic.fraud.detected", "fraud.events.detected");
        props.setProperty("provider.a-url", "http://localhost:8081");
        props.setProperty("provider.b-url", "http://localhost:8082");
        context.getPropertiesComponent().setOverrideProperties(props);

        KafkaComponent kafka = new KafkaComponent();
        KafkaConfiguration kafkaConfig = new KafkaConfiguration();
        kafkaConfig.setBrokers("localhost:9092");
        kafka.setConfiguration(kafkaConfig);
        context.addComponent("kafka", kafka);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ProviderRouterBean routerBean = new ProviderRouterBean();
        setField(routerBean, "providerConfig", providerConfig);

        ProviderSelectionRoute route = new ProviderSelectionRoute();
        setField(route, "objectMapper", objectMapper);
        setField(route, "providerConfig", providerConfig);

        context.getRegistry().bind("providerRouterBean", routerBean);
        context.addRoutes(route);
        context.start();
        producerTemplate = context.createProducerTemplate();

        assertNotNull(context.getRoute("provider-selection"));
        assertNotNull(context.getRoute("provider-a"));
        assertNotNull(context.getRoute("call-provider-a"));
        assertNotNull(context.getRoute("provider-b-fallback"));
        assertNotNull(context.getRoute("call-provider-b"));
        assertNotNull(context.getRoute("dead-letter"));
    }

    private Exchange exchangeWithResponse(ProviderResponse resp) {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getIn().setBody(resp);
        return exchange;
    }

    @Test
    @DisplayName("failOnProviderError throws with provider A message")
    void testFailOnProviderError_providerA() {
        ProviderResponse resp = new ProviderResponse(
            "provider-a", "tx-1", false, "DECLINED", "declined", LocalDateTime.now());
        Exchange exchange = exchangeWithResponse(resp);
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> ProviderSelectionRoute.failOnProviderError(exchange, "Provider A"));
        assertEquals("Provider A error: declined", ex.getMessage());
    }

    @Test
    @DisplayName("failOnProviderError throws with provider B message")
    void testFailOnProviderError_providerB() {
        ProviderResponse resp = new ProviderResponse(
            "provider-b", "tx-2", false, "TIMEOUT", "timed out", LocalDateTime.now());
        Exchange exchange = exchangeWithResponse(resp);
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> ProviderSelectionRoute.failOnProviderError(exchange, "Provider B"));
        assertEquals("Provider B error: timed out", ex.getMessage());
    }
}
