package com.poc.processor.application;

import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.domain.exception.PaymentProcessingException;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.processor.port.inbound.ProcessPaymentUseCase;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentProcessingService implements ProcessPaymentUseCase {

    private static final Logger LOG = Logger.getLogger(PaymentProcessingService.class);

    @Inject
    private EnrichPaymentUseCase enrichPaymentUseCase;

    @Inject
    private EvaluateFraudUseCase evaluateFraudUseCase;

    @Override
    public void execute(PaymentMessage message) {
        validatePaymentMessage(message);

        PaymentMessage enriched = enrichPaymentUseCase.enrich(message);

        FraudEvaluation evaluation = evaluateFraudUseCase.evaluate(enriched);

        LOG.infof("Payment processed: %s -> %s", enriched.paymentId(), evaluation.action());
    }

    public static void validatePaymentMessage(PaymentMessage msg) {
        if (msg.paymentId() == null || msg.amount() == null || msg.currency() == null
            || msg.customerId() == null || msg.paymentMethod() == null) {
            throw new PaymentProcessingException(
                msg.paymentId() != null ? msg.paymentId() : "unknown",
                "Invalid payment fields: required fields missing for payment " + msg.paymentId());
        }
    }
}
