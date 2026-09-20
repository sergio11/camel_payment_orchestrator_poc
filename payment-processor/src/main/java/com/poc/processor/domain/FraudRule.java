package com.poc.processor.domain;

public enum FraudRule {
    HIGH_AMOUNT("HIGH_AMOUNT"),
    HIGH_RISK_COUNTRY("HIGH_RISK_COUNTRY"),
    UNUSUAL_HOUR("UNUSUAL_HOUR"),
    RAPID_RETRY("RAPID_RETRY"),
    NEW_PAYMENT_METHOD("NEW_PAYMENT_METHOD"),
    HIGH_RISK_TIER("HIGH_RISK_TIER");

    private final String ruleName;

    FraudRule(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleName() {
        return ruleName;
    }
}
