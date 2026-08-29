package com.poc.processor.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class FraudRulesConfig {

    @ConfigProperty(name = "fraud.rules.high-amount-threshold")
    BigDecimal highAmountThreshold;

    @ConfigProperty(name = "fraud.rules.high-risk-countries")
    List<String> highRiskCountries;

    @ConfigProperty(name = "fraud.rules.rapid-retry-threshold")
    int rapidRetryThreshold;

    @ConfigProperty(name = "fraud.rules.new-method-days-threshold")
    int newMethodDaysThreshold;

    @ConfigProperty(name = "fraud.rules.unusual-hour-start")
    int unusualHourStart;

    @ConfigProperty(name = "fraud.rules.unusual-hour-end")
    int unusualHourEnd;

    @ConfigProperty(name = "fraud.rules.max-risk-score")
    int maxRiskScore;

    @ConfigProperty(name = "fraud.rules.risk-score-threshold-high")
    int riskScoreThresholdHigh;

    @ConfigProperty(name = "fraud.rules.risk-score-threshold-medium")
    int riskScoreThresholdMedium;

    @ConfigProperty(name = "fraud.rules.cbr-high-amount-threshold")
    BigDecimal cbrHighAmountThreshold;

    @ConfigProperty(name = "fraud.rules.cbr-wallet-amount-threshold")
    BigDecimal cbrWalletAmountThreshold;

    public BigDecimal highAmountThreshold() { return highAmountThreshold; }
    public List<String> highRiskCountries() { return highRiskCountries; }
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
