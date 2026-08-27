package com.poc.processor;

import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.processor.config.FraudRulesConfig;
import com.poc.processor.processor.FraudEvaluationProcessor;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FraudEvaluationProcessorTest {

    @Inject
    FraudEvaluationProcessor processor;

    @Inject
    FraudRulesConfig config;

    private Exchange createExchange(PaymentMessage message) {
        DefaultCamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(message);
        return exchange;
    }

    private PaymentMessage createPaymentMessage(String country, BigDecimal amount, Map<String, Object> metadata) {
        return new PaymentMessage(
            "event-1", "payment-1", amount, "USD", "customer-1",
            "CREDIT_CARD", country, 0, false, 0, "UTC", metadata != null ? metadata : Map.of(), LocalDateTime.now()
        );
    }

    @Test
    void testHighAmountScoring() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("20000"), Map.of());
        Exchange exchange = createExchange(msg);
        processor.process(exchange);
        FraudResult result = exchange.getIn().getBody(FraudResult.class);
        assertTrue(result.riskScore() >= 50, "High amount should add +50");
    }

    @Test
    void testHighRiskCountryScoring() {
        PaymentMessage msg = createPaymentMessage("XX", new BigDecimal("100"), Map.of());
        Exchange exchange = createExchange(msg);
        processor.process(exchange);
        FraudResult result = exchange.getIn().getBody(FraudResult.class);
        assertTrue(result.riskScore() >= 30, "High risk country should add +30");
    }

    @Test
    void testRapidRetryScoring() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), Map.of("attempts", 5));
        Exchange exchange = createExchange(msg);
        processor.process(exchange);
        FraudResult result = exchange.getIn().getBody(FraudResult.class);
        assertTrue(result.riskScore() >= 25, "Rapid retry should add +25");
    }

    @Test
    void testCombinedRules() {
        PaymentMessage msg = createPaymentMessage("XX", new BigDecimal("20000"), Map.of());
        Exchange exchange = createExchange(msg);
        processor.process(exchange);
        FraudResult result = exchange.getIn().getBody(FraudResult.class);
        assertEquals("REJECT", result.action());
        assertTrue(result.riskScore() >= 80);
    }

    @Test
    void testDetermineActionApprove() {
        PaymentMessage msg = createPaymentMessage("US", new BigDecimal("100"), Map.of());
        Exchange exchange = createExchange(msg);
        processor.process(exchange);
        FraudResult result = exchange.getIn().getBody(FraudResult.class);
        assertEquals("APPROVE", result.action());
    }
}
