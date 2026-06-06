package com.poc.processor.processor;

import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.config.FraudRulesConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@ApplicationScoped
public class FraudEvaluationProcessor implements Processor {

    @Inject
    FraudRulesConfig config;

    @Override
    public void process(Exchange exchange) {
        PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);
        
        int riskScore = calculateRiskScore(message);
        riskScore = Math.min(riskScore, config.maxRiskScore());
        
        FraudResult result = determineAction(message, riskScore);
        
        exchange.getIn().setBody(result);
        exchange.getIn().setHeader("CamelRiskScore", riskScore);
        exchange.getIn().setHeader("CamelFraudAction", result.action());
    }

    private int calculateRiskScore(PaymentMessage message) {
        int score = 0;
        
        // HIGH_AMOUNT rule: amount > 15000 → +50 score
        if (message.amount().compareTo(config.highAmountThreshold()) > 0) {
            score += 50;
        }
        
        // HIGH_RISK_COUNTRY rule: configured countries → +30 score
        if (config.highRiskCountries().contains(message.country())) {
            score += 30;
        }
        
        // RAPID_RETRY rule: attempts > 3 → +25 score
        if (message.metadata() != null) {
            Object attempts = message.metadata().get("attempts");
            if (attempts instanceof Number && ((Number) attempts).intValue() > config.rapidRetryThreshold()) {
                score += 25;
            }
            
            // NEW_PAYMENT_METHOD rule: new method + age < 30 days → +20 score
            Object methodAge = message.metadata().get("paymentMethodAgeDays");
            if (methodAge instanceof Number && ((Number) methodAge).intValue() < config.newMethodDaysThreshold()) {
                score += 20;
            }
            
            // UNUSUAL_HOUR rule: 2am-5am → +15 score
            int hour = message.timestamp().getHour();
            if (hour >= config.unusualHourStart() && hour <= config.unusualHourEnd()) {
                score += 15;
            }
        }
        
        return score;
    }

    private FraudResult determineAction(PaymentMessage message, int riskScore) {
        if (riskScore >= 80) {
            return FraudResult.reject(message.paymentId(), riskScore, "High risk score: " + riskScore);
        }
        if (riskScore >= 50) {
            return FraudResult.review(message.paymentId(), riskScore, "Medium risk score: " + riskScore);
        }
        return FraudResult.approve(message.paymentId(), riskScore);
    }
}