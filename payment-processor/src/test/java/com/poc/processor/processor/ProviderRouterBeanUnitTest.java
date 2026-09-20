package com.poc.processor.processor;

import com.poc.processor.config.ProviderConfig;
import com.poc.processor.route.CamelRouteConstants;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderRouterBeanUnitTest {

    @Mock
    ProviderConfig providerConfig;

    @InjectMocks
    ProviderRouterBean bean;

    @BeforeEach
    void setUp() {
        lenient().when(providerConfig.primaryProvider()).thenReturn("provider-a");
    }

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
    void testRouteToProvider_firstThenNull() {
        Map<String, Object> props = new HashMap<>();
        assertEquals("direct:provider-a", bean.routeToProvider(props));
        assertNull(bean.routeToProvider(props));
    }

    @Test
    @DisplayName("restoreOriginalHeaders does nothing without OriginalPaymentMessage")
    void testRestoreOriginalHeaders_noOrig() {
        Exchange exchange = exchangeWith("body");
        bean.restoreOriginalHeaders(exchange);
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("body", exchange.getMessage().getBody());
    }

    @Test
    @DisplayName("restoreOriginalHeaders fills missing headers from original")
    void testRestoreOriginalHeaders_fillsHeaders() {
        PaymentMessage orig = payment("pay-1");
        Exchange exchange = exchangeWith("body");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
        bean.restoreOriginalHeaders(exchange);
        assertEquals("pay-1", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("event-1", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("restoreOriginalHeaders keeps pre-existing headers")
    void testRestoreOriginalHeaders_keepsExisting() {
        PaymentMessage orig = payment("pay-1");
        Exchange exchange = exchangeWith("body");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, "kept-id");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, "kept-event");
        bean.restoreOriginalHeaders(exchange);
        assertEquals("kept-id", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("kept-event", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("prepareProviderCall without original keeps body, stores fraud result property")
    void testPrepareProviderCall_noOrig() {
        Exchange exchange = exchangeWith("fraud-result");
        bean.prepareProviderCall(exchange);
        assertEquals("fraud-result", exchange.getProperty("CamelFraudResult"));
        assertEquals("fraud-result", exchange.getMessage().getBody());
    }

    @Test
    @DisplayName("prepareProviderCall with original swaps body and fills headers")
    void testPrepareProviderCall_withOrig() {
        PaymentMessage orig = payment("pay-2");
        Exchange exchange = exchangeWith("fraud-result");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
        bean.prepareProviderCall(exchange);
        assertEquals("fraud-result", exchange.getProperty("CamelFraudResult"));
        assertEquals(orig, exchange.getMessage().getBody());
        assertEquals("pay-2", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("event-1", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("prepareProviderCall with original keeps pre-existing headers")
    void testPrepareProviderCall_withOrig_keepsHeaders() {
        PaymentMessage orig = payment("pay-2");
        Exchange exchange = exchangeWith("fraud-result");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, "kept-id");
        bean.prepareProviderCall(exchange);
        assertEquals(orig, exchange.getMessage().getBody());
        assertEquals("kept-id", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals("event-1", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("restoreDeadLetter without original keeps body")
    void testRestoreDeadLetter_noOrig() {
        Exchange exchange = exchangeWith("poison");
        bean.restoreDeadLetter(exchange);
        assertEquals("poison", exchange.getMessage().getBody());
    }

    @Test
    @DisplayName("restoreDeadLetter with original swaps body and fills headers")
    void testRestoreDeadLetter_withOrig() {
        PaymentMessage orig = payment("pay-3");
        Exchange exchange = exchangeWith("poison");
        exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
        bean.restoreDeadLetter(exchange);
        assertEquals(orig, exchange.getMessage().getBody());
        assertEquals("pay-3", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
    }

    @Test
    @DisplayName("restoreHeadersAfterHttpCall restores all headers from properties")
    void testRestoreHeadersAfterHttpCall_allSet() {
        PaymentMessage orig = payment("pay-4");
        Exchange exchange = exchangeWith("http-response");
        exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, "pay-4");
        exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
        exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, "event-1");
        bean.restoreHeadersAfterHttpCall(exchange);
        assertEquals("pay-4", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertEquals(orig, exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE));
        assertEquals("event-1", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("restoreHeadersAfterHttpCall does nothing without properties")
    void testRestoreHeadersAfterHttpCall_noneSet() {
        Exchange exchange = exchangeWith("http-response");
        bean.restoreHeadersAfterHttpCall(exchange);
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE));
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }

    @Test
    @DisplayName("restoreHeadersAfterHttpCall restores only paymentId when others missing")
    void testRestoreHeadersAfterHttpCall_partial() {
        Exchange exchange = exchangeWith("http-response");
        exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, "pay-5");
        bean.restoreHeadersAfterHttpCall(exchange);
        assertEquals("pay-5", exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID));
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE));
        assertNull(exchange.getMessage().getHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID));
    }
}
