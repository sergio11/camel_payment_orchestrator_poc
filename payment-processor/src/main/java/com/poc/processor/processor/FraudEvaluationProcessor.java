package com.poc.processor.processor;

import com.poc.shared.dto.PaymentMetadataDTO;
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
        
        exchange.getIn().setHeader("OriginalPaymentMessage", message);
        
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
        if (message.timeZone() != null && ZoneId.getAvailableZoneIds().contains(message.timeZone())) {
            hour = LocalTime.now(ZoneId.of(message.timeZone())).getHour();
        } else {
            hour = LocalTime.now(ZoneId.of("UTC")).getHour();
        }
        if (hour >= config.unusualHourStart() && hour <= config.unusualHourEnd()) {
            score += 15;
            triggeredRules.add("UNUSUAL_HOUR");
        }
        
        PaymentMetadataDTO metadata = message.metadata();
        if (metadata != null) {
            int attempts = metadata.attempts() != null ? metadata.attempts() : 0;
            if (attempts > config.rapidRetryThreshold()) {
                score += 25;
                triggeredRules.add("RAPID_RETRY");
            }
            
            boolean isNewMethod = metadata.isNewPaymentMethod() != null && metadata.isNewPaymentMethod();
            int methodAge = metadata.paymentMethodAgeDays() != null ? metadata.paymentMethodAgeDays() : Integer.MAX_VALUE;
            if (isNewMethod && methodAge < config.newMethodDaysThreshold()) {
                score += 20;
                triggeredRules.add("NEW_PAYMENT_METHOD");
            }

            String riskTier = metadata.customerRiskTier();
            if ("HIGH".equalsIgnoreCase(riskTier)) {
                score += 10;
                triggeredRules.add("HIGH_RISK_TIER");
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
