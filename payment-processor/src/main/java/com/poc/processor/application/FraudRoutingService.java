package com.poc.processor.application;

import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.processor.ContentBasedRouterBean;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FraudRoutingService {

    @Inject
    ContentBasedRouterBean contentBasedRouterBean;

    public FraudRoutingDecision route(PaymentMessage message, FraudEvaluation evaluation) {
        String target = contentBasedRouterBean.routeToFraudCheck(
            message.amount(), message.paymentMethod(), message.country()
        );
        return new FraudRoutingDecision(evaluation.action(), target, evaluation);
    }

    public record FraudRoutingDecision(
        FraudAction action,
        String routeTarget,
        FraudEvaluation evaluation
    ) {}
}
