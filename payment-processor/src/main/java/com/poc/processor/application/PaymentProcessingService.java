package com.poc.processor.application;

import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
import com.poc.processor.port.inbound.ProcessPaymentUseCase;
import com.poc.processor.port.outbound.AuditEventPublisherPort;
import com.poc.processor.port.outbound.FraudDetectionPublisherPort;
import com.poc.processor.port.outbound.PaymentEventPublisherPort;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentProcessingService implements ProcessPaymentUseCase {

    private static final Logger LOG = Logger.getLogger(PaymentProcessingService.class);

    @Inject
    EnrichPaymentUseCase enrichPaymentUseCase;

    @Inject
    EvaluateFraudUseCase evaluateFraudUseCase;

    @Inject
    FraudDetectionPublisherPort fraudDetectionPublisher;

    @Inject
    AuditEventPublisherPort auditPublisher;

    @Override
    public void execute(PaymentMessage message) {
        validatePaymentMessage(message);

        PaymentMessage enriched = enrichPaymentUseCase.enrich(message);

        auditPublisher.publishAudit(enriched.paymentId(), "PAYMENT_RECEIVED");

        FraudEvaluation evaluation = evaluateFraudUseCase.evaluate(enriched);

        switch (evaluation.action()) {
            case FraudEvaluation.ACTION_REJECT -> {
                LOG.infof("Fraud REJECT for payment %s: %s", enriched.paymentId(), evaluation.reason());
                fraudDetectionPublisher.publishFraudDetected(evaluation);
                paymentEventPublisher().publishFailed(enriched, evaluation.reason());
            }
            case FraudEvaluation.ACTION_REVIEW -> {
                LOG.infof("Fraud REVIEW for payment %s: %s", enriched.paymentId(), evaluation.reason());
                fraudDetectionPublisher.publishFraudDetected(evaluation);
                paymentEventPublisher().publishFailed(enriched, evaluation.reason());
            }
            default -> {
                LOG.infof("Fraud APPROVE for payment %s", enriched.paymentId());
            }
        }

        LOG.infof("Payment processed: %s -> %s", enriched.paymentId(), evaluation.action());
    }

    private PaymentEventPublisherPort paymentEventPublisher() {
        return paymentEventPublisher;
    }

    @Inject
    PaymentEventPublisherPort paymentEventPublisher;

    public static void validatePaymentMessage(PaymentMessage msg) {
        if (msg.paymentId() == null || msg.amount() == null || msg.currency() == null
            || msg.customerId() == null || msg.paymentMethod() == null) {
            throw new IllegalArgumentException(
                "Invalid payment fields: required fields missing for payment " + msg.paymentId());
        }
    }
}
