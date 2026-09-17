package com.poc.processor.processor;

import com.poc.processor.config.FraudRulesConfig;
import com.poc.processor.port.outbound.RoutingDecisionPort;
import com.poc.processor.route.CamelRouteConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;

@ApplicationScoped
public class ContentBasedRouterBean implements RoutingDecisionPort {

    @Inject
    private FraudRulesConfig config;

    @Override
    public String resolveFraudRoute(BigDecimal amount, String paymentMethod, String country) {
        return routeToFraudCheck(amount, paymentMethod, country);
    }

    public String routeToFraudCheck(BigDecimal amount, String paymentMethod, String country) {
        if (amount == null) {
            return CamelRouteConstants.DIRECT_FRAUD_CHECK;
        }
        if (amount.compareTo(config.cbrHighAmountThreshold()) > 0) {
            return CamelRouteConstants.DIRECT_FRAUD_REVIEW;
        }
        if (country != null && config.highRiskCountries().contains(country)) {
            return CamelRouteConstants.DIRECT_FRAUD_REVIEW;
        }
        if ("WALLET".equals(paymentMethod) && amount.compareTo(config.cbrWalletAmountThreshold()) > 0) {
            return CamelRouteConstants.DIRECT_FRAUD_REVIEW;
        }
        return CamelRouteConstants.DIRECT_FRAUD_CHECK;
    }
}
