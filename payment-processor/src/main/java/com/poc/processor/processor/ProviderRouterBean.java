package com.poc.processor.processor;

import com.poc.shared.config.ProviderConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProviderRouterBean {

    @Inject
    ProviderConfig config;

    public String routeToProvider(String exchange) {
        return "direct:provider-a";
    }

    public String routeToFallback(String exchange) {
        return "direct:provider-b";
    }

    public String providerAUrl() {
        return config.providerAUrl();
    }

    public String providerBUrl() {
        return config.providerBUrl();
    }
}