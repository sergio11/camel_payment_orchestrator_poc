package com.poc.processor.processor;

import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
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
}
