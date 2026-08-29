package com.poc.processor.processor;

import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.processor.config.FraudRulesConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@Named("fraudEvaluationProcessor")
@ApplicationScoped
public class FraudEvaluationProcessor implements Processor {

    @Inject
    FraudRulesConfig config;

    @Override
    public void process(Exchange exchange) {
        PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);
        
        List<String> triggeredRules = new ArrayList<>();
        int riskScore = calculateRiskScore(message, triggeredRules);
        riskScore = Math.min(riskScore, config.maxRiskScore());
        
        FraudResult result = determineAction(message, riskScore, triggeredRules);
        
        exchange.getIn().setBody(result);
        exchange.getIn().setHeader("CamelRiskScore", riskScore);
        exchange.getIn().setHeader("CamelFraudAction", result.action());
    }

    private int calculateRiskScore(PaymentMessage message, List<String> triggeredRules) {
        int score = 0;
        
        if (message.amount().compareTo(config.highAmountThreshold()) > 0) {
            score += 50;
            triggeredRules.add("HIGH_AMOUNT");
        }
        
        if (config.highRiskCountries().contains(message.country())) {
            score += 30;
            triggeredRules.add("HIGH_RISK_COUNTRY");
        }
        
        int hour;
        if (message.timeZone() != null) {
            try {
                hour = LocalTime.now(message.timeZone()).getHour();
            } catch (Exception e) {
                hour = LocalTime.now(ZoneId.of("UTC")).getHour();
            }
        } else {
            hour = LocalTime.now(ZoneId.of("UTC")).getHour();
        }
        if (hour >= config.unusualHourStart() && hour <= config.unusualHourEnd()) {
            score += 15;
            triggeredRules.add("UNUSUAL_HOUR");
        }
        
        if (message.metadata() != null) {
            Object attempts = message.metadata().get("attempts");
            if (attempts instanceof Number && ((Number) attempts).intValue() > config.rapidRetryThreshold()) {
                score += 25;
                triggeredRules.add("RAPID_RETRY");
            }
            
            Object isNewMethod = message.metadata().get("isNewPaymentMethod");
            Object methodAge = message.metadata().get("paymentMethodAgeDays");
            if (Boolean.TRUE.equals(isNewMethod) && methodAge instanceof Number && ((Number) methodAge).intValue() < config.newMethodDaysThreshold()) {
                score += 20;
                triggeredRules.add("NEW_PAYMENT_METHOD");
            }
        }
        
        return score;
    }

    private FraudResult determineAction(PaymentMessage message, int riskScore, List<String> triggeredRules) {
        if (riskScore >= config.riskScoreThresholdHigh()) {
            return FraudResult.reject(message.paymentId(), message.amount(), message.customerId(), riskScore, "High risk score: " + riskScore, triggeredRules);
        }
        if (riskScore >= config.riskScoreThresholdMedium()) {
            return FraudResult.review(message.paymentId(), message.amount(), message.customerId(), riskScore, "Medium risk score: " + riskScore, triggeredRules);
        }
        return FraudResult.approve(message.paymentId(), message.amount(), message.customerId(), riskScore, triggeredRules);
    }
}
