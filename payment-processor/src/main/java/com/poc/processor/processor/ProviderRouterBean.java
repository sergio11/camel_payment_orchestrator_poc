package com.poc.processor.processor;

import com.poc.processor.config.ProviderConfig;
import com.poc.processor.route.CamelRouteConstants;
import com.poc.shared.event.PaymentMessage;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Handler;

@ApplicationScoped
@Named("providerRouterBean")
public class ProviderRouterBean {

    private static final String ROUTED_KEY = "providerRouted";

    @Inject
    private ProviderConfig providerConfig;

    @Handler
    public String routeToProvider(@ExchangeProperties Map<String, Object> properties) {
        if (properties.containsKey(ROUTED_KEY)) {
            return null;
        }
        properties.put(ROUTED_KEY, Boolean.TRUE);
        return "direct:" + providerConfig.primaryProvider();
    }

    public void restoreOriginalHeaders(Exchange exchange) {
        PaymentMessage orig = exchange.getMessage().getHeader(
            CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, PaymentMessage.class);
        if (orig != null) {
            CamelRouteConstants.setHeaderIfAbsent(exchange, CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, orig.paymentId());
            CamelRouteConstants.setHeaderIfAbsent(exchange, CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, orig.eventId());
        }
    }

    public void prepareProviderCall(Exchange exchange) {
        exchange.setProperty("CamelFraudResult", exchange.getMessage().getBody());
        PaymentMessage orig = exchange.getMessage().getHeader(
            CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, PaymentMessage.class);
        if (orig != null) {
            exchange.getMessage().setBody(orig);
            restoreOriginalHeaders(exchange);
            exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, orig.paymentId());
            exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, orig);
            exchange.setProperty(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, orig.eventId());
        }
    }

    public void restoreHeadersAfterHttpCall(Exchange exchange) {
        String paymentId = (String) exchange.getProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID);
        if (paymentId != null) {
            exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID, paymentId);
        }
        PaymentMessage origMsg = (PaymentMessage) exchange.getProperty(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE);
        if (origMsg != null) {
            exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, origMsg);
        }
        String eventId = (String) exchange.getProperty(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID);
        if (eventId != null) {
            exchange.getMessage().setHeader(CamelRouteConstants.HEADER_ORIGINAL_EVENT_ID, eventId);
        }
    }

    public void restoreDeadLetter(Exchange exchange) {
        PaymentMessage orig = exchange.getMessage().getHeader(
            CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE, PaymentMessage.class);
        if (orig != null) {
            exchange.getMessage().setBody(orig);
            restoreOriginalHeaders(exchange);
        }
    }
}
