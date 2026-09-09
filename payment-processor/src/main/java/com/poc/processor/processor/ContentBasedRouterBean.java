package com.poc.processor.processor;

import com.poc.processor.config.FraudRulesConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;

@ApplicationScoped
public class ContentBasedRouterBean {

    public static final String DESTINATION_FRAUD_CHECK = "direct:fraud-check";
    public static final String DESTINATION_FRAUD_REVIEW = "direct:fraud-review";

    @Inject
    FraudRulesConfig config;

    public String routeToFraudCheck(BigDecimal amount, String paymentMethod, String country) {
        if (amount == null) {
            return DESTINATION_FRAUD_CHECK;
        }
        if (amount.compareTo(config.cbrHighAmountThreshold()) > 0) {
            return DESTINATION_FRAUD_REVIEW;
        }
        if (country != null && config.highRiskCountries().contains(country)) {
            return DESTINATION_FRAUD_REVIEW;
        }
        if ("WALLET".equals(paymentMethod) && amount.compareTo(config.cbrWalletAmountThreshold()) > 0) {
            return DESTINATION_FRAUD_REVIEW;
        }
        return DESTINATION_FRAUD_CHECK;
    }
}
