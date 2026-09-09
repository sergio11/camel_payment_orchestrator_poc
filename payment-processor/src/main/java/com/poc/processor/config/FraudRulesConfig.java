package com.poc.processor.config;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class FraudRulesConfig {

    @ConfigProperty(name = "fraud.rules.high-amount-threshold")
    private BigDecimal highAmountThreshold;

    @ConfigProperty(name = "fraud.rules.high-risk-countries")
    private List<String> highRiskCountries;

    @ConfigProperty(name = "fraud.rules.rapid-retry-threshold")
    private int rapidRetryThreshold;

    @ConfigProperty(name = "fraud.rules.new-method-days-threshold")
    private int newMethodDaysThreshold;

    @ConfigProperty(name = "fraud.rules.unusual-hour-start")
    private int unusualHourStart;

    @ConfigProperty(name = "fraud.rules.unusual-hour-end")
    private int unusualHourEnd;

    @ConfigProperty(name = "fraud.rules.max-risk-score")
    private int maxRiskScore;

    @ConfigProperty(name = "fraud.rules.risk-score-threshold-high")
    private int riskScoreThresholdHigh;

    @ConfigProperty(name = "fraud.rules.risk-score-threshold-medium")
    private int riskScoreThresholdMedium;

    @ConfigProperty(name = "fraud.rules.cbr-high-amount-threshold")
    private BigDecimal cbrHighAmountThreshold;

    @ConfigProperty(name = "fraud.rules.cbr-wallet-amount-threshold")
    private BigDecimal cbrWalletAmountThreshold;

    @PostConstruct
    void validate() {
        if (unusualHourStart < 0 || unusualHourStart > 23) {
            throw new IllegalStateException("fraud.rules.unusual-hour-start must be 0-23, got: " + unusualHourStart);
        }
        if (unusualHourEnd < 0 || unusualHourEnd > 23) {
            throw new IllegalStateException("fraud.rules.unusual-hour-end must be 0-23, got: " + unusualHourEnd);
        }
        if (riskScoreThresholdHigh <= riskScoreThresholdMedium) {
            throw new IllegalStateException("risk-score-threshold-high must be > risk-score-threshold-medium");
        }
        if (cbrHighAmountThreshold.compareTo(cbrWalletAmountThreshold) <= 0) {
            throw new IllegalStateException("cbr-high-amount-threshold must be > cbr-wallet-amount-threshold");
        }
    }

    public BigDecimal highAmountThreshold() { return highAmountThreshold; }
    public List<String> highRiskCountries() {
        return highRiskCountries != null ? List.copyOf(highRiskCountries) : List.of();
    }
    public int rapidRetryThreshold() { return rapidRetryThreshold; }
    public int newMethodDaysThreshold() { return newMethodDaysThreshold; }
    public int unusualHourStart() { return unusualHourStart; }
    public int unusualHourEnd() { return unusualHourEnd; }
    public int maxRiskScore() { return maxRiskScore; }
    public int riskScoreThresholdHigh() { return riskScoreThresholdHigh; }
    public int riskScoreThresholdMedium() { return riskScoreThresholdMedium; }
    public BigDecimal cbrHighAmountThreshold() { return cbrHighAmountThreshold; }
    public BigDecimal cbrWalletAmountThreshold() { return cbrWalletAmountThreshold; }
}
