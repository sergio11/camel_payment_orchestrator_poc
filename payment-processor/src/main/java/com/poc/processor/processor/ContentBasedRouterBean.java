package com.poc.processor.processor;

import com.poc.shared.config.FraudRulesConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ContentBasedRouterBean {

    @Inject
    FraudRulesConfig config;

    public String routeToFraudCheck(double amount, String paymentMethod, String country) {
        if (amount > 10000) {
            return "direct:fraud-review";
        }
        if ("WALLET".equals(paymentMethod) && amount > 5000) {
            return "direct:fraud-review";
        }
        if (config.highRiskCountries().contains(country)) {
            return "direct:fraud-review";
        }
        return "direct:fraud-check";
    }
}