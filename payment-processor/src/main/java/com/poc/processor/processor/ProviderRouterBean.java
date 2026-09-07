package com.poc.processor.processor;

import com.poc.shared.event.PaymentMessage;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Handler;

@ApplicationScoped
public class ProviderRouterBean {

    private static final String ROUTED_KEY = "providerRouted";

    @Handler
    public String routeToProvider(@ExchangeProperties Map<String, Object> properties) {
        if (properties.containsKey(ROUTED_KEY)) {
            return null;
        }
        properties.put(ROUTED_KEY, Boolean.TRUE);
        return "direct:provider-a";
    }

    public void restoreOriginalHeaders(Exchange exchange) {
        PaymentMessage orig = exchange.getMessage().getHeader("OriginalPaymentMessage", PaymentMessage.class);
        if (orig != null) {
            if (exchange.getMessage().getHeader("OriginalPaymentId") == null) {
                exchange.getMessage().setHeader("OriginalPaymentId", orig.paymentId());
            }
            if (exchange.getMessage().getHeader("OriginalEventId") == null) {
                exchange.getMessage().setHeader("OriginalEventId", orig.eventId());
            }
        }
    }

    public void prepareProviderCall(Exchange exchange) {
        exchange.setProperty("CamelFraudResult", exchange.getMessage().getBody());
        PaymentMessage orig = exchange.getMessage().getHeader("OriginalPaymentMessage", PaymentMessage.class);
        if (orig != null) {
            exchange.getMessage().setBody(orig);
            restoreOriginalHeaders(exchange);
        }
    }

    public void restoreDeadLetter(Exchange exchange) {
        PaymentMessage orig = exchange.getMessage().getHeader("OriginalPaymentMessage", PaymentMessage.class);
        if (orig != null) {
            exchange.getMessage().setBody(orig);
            restoreOriginalHeaders(exchange);
        }
    }
}
