package com.poc.processor.processor;

import com.poc.processor.config.FraudRulesConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;

@ApplicationScoped
public class ContentBasedRouterBean {

    @Inject
    FraudRulesConfig config;

    public String routeToFraudCheck(BigDecimal amount, String paymentMethod, String country) {
        if (amount == null) {
            return "direct:fraud-check";
        }
        if (amount.compareTo(config.cbrHighAmountThreshold()) > 0) {
            return "direct:fraud-review";
        }
        if ("WALLET".equals(paymentMethod) && amount.compareTo(config.cbrWalletAmountThreshold()) > 0) {
            return "direct:fraud-review";
        }
        if (config.highRiskCountries().contains(country)) {
            return "direct:fraud-review";
        }
        return "direct:fraud-check";
    }
}
