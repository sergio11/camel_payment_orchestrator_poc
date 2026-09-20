package com.poc.processor.port.inbound;

import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.event.PaymentMessage;

public interface RouteFraudUseCase {
    FraudRoutingDecision route(PaymentMessage message, FraudEvaluation evaluation);

    record FraudRoutingDecision(
        FraudAction action,
        String routeTarget,
        FraudEvaluation evaluation
    ) {}
}
