package com.poc.processor.application;

import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.inbound.RouteFraudUseCase;
import com.poc.processor.port.outbound.RoutingDecisionPort;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FraudRoutingService implements RouteFraudUseCase {

    @Inject
    private RoutingDecisionPort routingPort;

    @Override
    public FraudRoutingDecision route(PaymentMessage message, FraudEvaluation evaluation) {
        String target = routingPort.resolveFraudRoute(
            message.amount(), message.paymentMethod(), message.country()
        );
        return new FraudRoutingDecision(evaluation.action(), target, evaluation);
    }
}
