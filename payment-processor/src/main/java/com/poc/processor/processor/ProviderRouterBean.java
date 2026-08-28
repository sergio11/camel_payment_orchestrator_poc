package com.poc.processor.processor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Handler;

@ApplicationScoped
public class ProviderRouterBean {

    private static final String ROUTED_KEY = "providerRouted";
    private final AtomicInteger counter = new AtomicInteger(0);

    @Handler
    public String routeToProvider(@ExchangeProperties Map<String, Object> properties) {
        if (properties.containsKey(ROUTED_KEY)) {
            return null;
        }
        properties.put(ROUTED_KEY, Boolean.TRUE);
        int idx = Math.floorMod(counter.getAndIncrement(), 2);
        return idx == 0 ? "direct:provider-a" : "direct:provider-b-fallback";
    }
}
