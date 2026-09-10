package com.poc.processor.port.inbound;

import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.event.PaymentMessage;

public interface EvaluateFraudUseCase {
    FraudEvaluation evaluate(PaymentMessage message);
}
