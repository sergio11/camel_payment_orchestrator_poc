package com.poc.processor.processor;

import com.poc.shared.dto.PaymentMetadataDTO;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProviderRouterBeanTest {

    @Inject
    ProviderRouterBean bean;

    private PaymentMessage payment(String paymentId) {
        return new PaymentMessage(
            "event-1", paymentId, new BigDecimal("100.00"), "USD", "customer-1",
            "CREDIT_CARD", "US", 0, false, 0, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    private Exchange exchangeWith(Object body) {
        DefaultCamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @Test
    @DisplayName("routeToProvider returns provider-a on first call, null afterwards")
    void routeToProvider_firstThenNull() {
        Map<String, Object> props = new HashMap<>();
        assertEquals("direct:provider-a", bean.routeToProvider(props));
        assertNull(bean.routeToProvider(props));
    }

    @Test
    @DisplayName("restoreOriginalHeaders does nothing without OriginalPaymentMessage")
    void restoreOriginalHeaders_noOrig_noOp() {
        Exchange exchange = exchangeWith("body");
        bean.restoreOriginalHeaders(exchange);
        assertNull(exchange.getMessage().getHeader("OriginalPaymentId"));
        assertEquals("body", exchange.getMessage().getBody());
    }

    @Test
    @DisplayName("restoreOriginalHeaders fills missing headers from original")
    void restoreOriginalHeaders_missingHeaders_fills() {
        PaymentMessage orig = payment("pay-1");
        Exchange exchange = exchangeWith("body");
        exchange.getMessage().setHeader("OriginalPaymentMessage", orig);
        bean.restoreOriginalHeaders(exchange);
        assertEquals("pay-1", exchange.getMessage().getHeader("OriginalPaymentId"));
        assertEquals("event-1", exchange.getMessage().getHeader("OriginalEventId"));
    }

    @Test
    @DisplayName("restoreOriginalHeaders keeps pre-existing headers")
    void restoreOriginalHeaders_existingHeaders_kept() {
        PaymentMessage orig = payment("pay-1");
        Exchange exchange = exchangeWith("body");
        exchange.getMessage().setHeader("OriginalPaymentMessage", orig);
        exchange.getMessage().setHeader("OriginalPaymentId", "kept-id");
        exchange.getMessage().setHeader("OriginalEventId", "kept-event");
        bean.restoreOriginalHeaders(exchange);
        assertEquals("kept-id", exchange.getMessage().getHeader("OriginalPaymentId"));
        assertEquals("kept-event", exchange.getMessage().getHeader("OriginalEventId"));
    }

    @Test
    @DisplayName("prepareProviderCall without original keeps body, stores fraud result property")
    void prepareProviderCall_noOrig_keepsBody() {
        Exchange exchange = exchangeWith("fraud-result");
        bean.prepareProviderCall(exchange);
        assertEquals("fraud-result", exchange.getProperty("CamelFraudResult"));
        assertEquals("fraud-result", exchange.getMessage().getBody());
    }

    @Test
    @DisplayName("prepareProviderCall with original swaps body and fills headers")
    void prepareProviderCall_withOrig_swapsBody() {
        PaymentMessage orig = payment("pay-2");
        Exchange exchange = exchangeWith("fraud-result");
        exchange.getMessage().setHeader("OriginalPaymentMessage", orig);
        bean.prepareProviderCall(exchange);
        assertEquals("fraud-result", exchange.getProperty("CamelFraudResult"));
        assertEquals(orig, exchange.getMessage().getBody());
        assertEquals("pay-2", exchange.getMessage().getHeader("OriginalPaymentId"));
        assertEquals("event-1", exchange.getMessage().getHeader("OriginalEventId"));
    }

    @Test
    @DisplayName("prepareProviderCall with original keeps pre-existing headers")
    void prepareProviderCall_withOrig_keepsHeaders() {
        PaymentMessage orig = payment("pay-2");
        Exchange exchange = exchangeWith("fraud-result");
        exchange.getMessage().setHeader("OriginalPaymentMessage", orig);
        exchange.getMessage().setHeader("OriginalPaymentId", "kept-id");
        bean.prepareProviderCall(exchange);
        assertEquals(orig, exchange.getMessage().getBody());
        assertEquals("kept-id", exchange.getMessage().getHeader("OriginalPaymentId"));
        assertEquals("event-1", exchange.getMessage().getHeader("OriginalEventId"));
    }

    @Test
    @DisplayName("restoreDeadLetter without original keeps body")
    void restoreDeadLetter_noOrig_noOp() {
        Exchange exchange = exchangeWith("poison");
        bean.restoreDeadLetter(exchange);
        assertEquals("poison", exchange.getMessage().getBody());
    }

    @Test
    @DisplayName("restoreDeadLetter with original swaps body and fills headers")
    void restoreDeadLetter_withOrig_restores() {
        PaymentMessage orig = payment("pay-3");
        Exchange exchange = exchangeWith("poison");
        exchange.getMessage().setHeader("OriginalPaymentMessage", orig);
        bean.restoreDeadLetter(exchange);
        assertEquals(orig, exchange.getMessage().getBody());
        assertEquals("pay-3", exchange.getMessage().getHeader("OriginalPaymentId"));
    }
}
