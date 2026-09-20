package com.poc.processor.application;

import com.poc.processor.config.FraudRulesConfig;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudRule;
import com.poc.processor.domain.exception.FraudEvaluationException;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FraudEvaluationService implements EvaluateFraudUseCase {

    @Inject
    private FraudRulesConfig config;

    @Override
    public FraudEvaluation evaluate(PaymentMessage message) {
        try {
            List<String> triggeredRules = new ArrayList<>();
            int riskScore = calculateRiskScore(message, triggeredRules);
            riskScore = Math.min(riskScore, config.maxRiskScore());
            return determineAction(message, riskScore, triggeredRules);
        } catch (Exception e) {
            throw new FraudEvaluationException(
                message.paymentId(),
                "Fraud evaluation failed for payment " + message.paymentId(),
                e
            );
        }
    }

    private int calculateRiskScore(PaymentMessage message, List<String> triggeredRules) {
        int score = 0;

        if (message.amount().compareTo(config.highAmountThreshold()) > 0) {
            score += 50;
            triggeredRules.add(FraudRule.HIGH_AMOUNT.getRuleName());
        }

        if (message.country() != null && config.highRiskCountries().contains(message.country())) {
            score += 30;
            triggeredRules.add(FraudRule.HIGH_RISK_COUNTRY.getRuleName());
        }

        int hour;
        if (message.timeZone() != null && ZoneId.getAvailableZoneIds().contains(message.timeZone())) {
            hour = LocalTime.now(ZoneId.of(message.timeZone())).getHour();
        } else {
            hour = LocalTime.now(ZoneId.of("UTC")).getHour();
        }
        if (hour >= config.unusualHourStart() && hour <= config.unusualHourEnd()) {
            score += 15;
            triggeredRules.add(FraudRule.UNUSUAL_HOUR.getRuleName());
        }

        PaymentMetadataDTO metadata = message.metadata();
        if (metadata != null) {
            int attempts = metadata.attempts() != null ? metadata.attempts() : 0;
            if (attempts > config.rapidRetryThreshold()) {
                score += 25;
                triggeredRules.add(FraudRule.RAPID_RETRY.getRuleName());
            }

            boolean isNewMethod = metadata.isNewPaymentMethod() != null && metadata.isNewPaymentMethod();
            int methodAge = metadata.paymentMethodAgeDays() != null ? metadata.paymentMethodAgeDays() : Integer.MAX_VALUE;
            if (isNewMethod && methodAge < config.newMethodDaysThreshold()) {
                score += 20;
                triggeredRules.add(FraudRule.NEW_PAYMENT_METHOD.getRuleName());
            }

            String riskTier = metadata.customerRiskTier();
            if ("HIGH".equalsIgnoreCase(riskTier)) {
                score += 10;
                triggeredRules.add(FraudRule.HIGH_RISK_TIER.getRuleName());
            }
        }

        return score;
    }

    private FraudEvaluation determineAction(PaymentMessage message, int riskScore, List<String> triggeredRules) {
        if (riskScore >= config.riskScoreThresholdHigh()) {
            return new FraudEvaluation(
                message.paymentId(), message.amount(), message.customerId(),
                riskScore, FraudAction.REJECT,
                "High risk score: " + riskScore, triggeredRules
            );
        }
        if (riskScore >= config.riskScoreThresholdMedium()) {
            return new FraudEvaluation(
                message.paymentId(), message.amount(), message.customerId(),
                riskScore, FraudAction.REVIEW,
                "Medium risk score: " + riskScore, triggeredRules
            );
        }
        return new FraudEvaluation(
            message.paymentId(), message.amount(), message.customerId(),
                riskScore, FraudAction.APPROVE,
                null, triggeredRules
        );
    }
}
