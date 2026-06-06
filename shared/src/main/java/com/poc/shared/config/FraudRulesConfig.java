package com.poc.shared.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class FraudRulesConfig {

    @ConfigProperty(name = "fraud.rules.high-amount-threshold", defaultValue = "15000")
    BigDecimal highAmountThreshold;

    @ConfigProperty(name = "fraud.rules.high-risk-countries", defaultValue = "XX,YY")
    List<String> highRiskCountries;

    @ConfigProperty(name = "fraud.rules.rapid-retry-threshold", defaultValue = "3")
    int rapidRetryThreshold;

    @ConfigProperty(name = "fraud.rules.new-method-days-threshold", defaultValue = "30")
    int newMethodDaysThreshold;

    @ConfigProperty(name = "fraud.rules.unusual-hour-start", defaultValue = "2")
    int unusualHourStart;

    @ConfigProperty(name = "fraud.rules.unusual-hour-end", defaultValue = "5")
    int unusualHourEnd;

    @ConfigProperty(name = "fraud.rules.max-risk-score", defaultValue = "100")
    int maxRiskScore;

    public BigDecimal highAmountThreshold() { return highAmountThreshold; }
    public List<String> highRiskCountries() { return highRiskCountries; }
    public int rapidRetryThreshold() { return rapidRetryThreshold; }
    public int newMethodDaysThreshold() { return newMethodDaysThreshold; }
    public int unusualHourStart() { return unusualHourStart; }
    public int unusualHourEnd() { return unusualHourEnd; }
    public int maxRiskScore() { return maxRiskScore; }
}