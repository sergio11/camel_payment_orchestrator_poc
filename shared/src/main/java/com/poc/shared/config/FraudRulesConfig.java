package com.poc.shared.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
@ConfigProperties(prefix = "fraud.rules")
public class FraudRulesConfig {

    BigDecimal highAmountThreshold;
    List<String> highRiskCountries;
    int rapidRetryThreshold;
    int newMethodDaysThreshold;
    int unusualHourStart;
    int unusualHourEnd;
    int maxRiskScore;

    public BigDecimal highAmountThreshold() { return highAmountThreshold; }
    public List<String> highRiskCountries() { return highRiskCountries; }
    public int rapidRetryThreshold() { return rapidRetryThreshold; }
    public int newMethodDaysThreshold() { return newMethodDaysThreshold; }
    public int unusualHourStart() { return unusualHourStart; }
    public int unusualHourEnd() { return unusualHourEnd; }
    public int maxRiskScore() { return maxRiskScore; }
}