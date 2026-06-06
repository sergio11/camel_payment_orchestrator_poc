package com.poc.processor.processor;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ContentBasedRouterBean {

    public String routeToFraudCheck(double amount, String paymentMethod) {
        if (amount > 10000) {
            return "direct:fraud-review";
        }
        if ("WALLET".equals(paymentMethod) && amount > 5000) {
            return "direct:fraud-review";
        }
        return "direct:fraud-check";
    }
}