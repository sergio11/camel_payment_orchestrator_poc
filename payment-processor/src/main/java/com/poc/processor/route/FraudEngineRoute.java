package com.poc.processor.route;

import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.outbound.FraudDetectionPublisherPort;
import com.poc.processor.port.outbound.PaymentEventPublisherPort;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class FraudEngineRoute extends RouteBuilder {

    @Inject
    FraudDetectionPublisherPort fraudDetectionPublisher;

    @Inject
    PaymentEventPublisherPort paymentEventPublisher;

    @Override
    public void configure() {
        from("direct:fraud-reject")
            .routeId("fraud-reject")
            .process(exchange -> {
                FraudEvaluation evaluation = exchange.getProperty("FraudEvaluation", FraudEvaluation.class);
                PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);

                if (evaluation == null) {
                    evaluation = new FraudEvaluation(
                        message.paymentId(), message.amount(), message.customerId(),
                        100, FraudEvaluation.ACTION_REJECT,
                        "High risk", java.util.List.of()
                    );
                }

                fraudDetectionPublisher.publishFraudDetected(evaluation);
                paymentEventPublisher.publishFailed(message, evaluation.reason());
            })
            .log("Fraud rejection processed for: ${body.paymentId}");

        from("direct:fraud-review-queue")
            .routeId("fraud-review-queue")
            .process(exchange -> {
                FraudEvaluation evaluation = exchange.getProperty("FraudEvaluation", FraudEvaluation.class);
                PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);

                if (evaluation == null) {
                    evaluation = new FraudEvaluation(
                        message.paymentId(), message.amount(), message.customerId(),
                        60, FraudEvaluation.ACTION_REVIEW,
                        "Medium risk", java.util.List.of()
                    );
                }

                fraudDetectionPublisher.publishFraudDetected(evaluation);
                paymentEventPublisher.publishFailed(message, evaluation.reason());
            })
            .log("Fraud review processed for: ${body.paymentId}");
    }
}
