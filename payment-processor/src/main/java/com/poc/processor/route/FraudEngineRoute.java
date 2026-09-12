package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;

import java.util.List;

@ApplicationScoped
public class FraudEngineRoute extends RouteBuilder {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void configure() {
        JacksonDataFormat fraudResultJson = new JacksonDataFormat(objectMapper, FraudResult.class);
        JacksonDataFormat paymentJson = new JacksonDataFormat(objectMapper, PaymentMessage.class);

        from("direct:fraud-reject")
            .routeId("fraud-reject")
            .process(exchange -> {
                FraudEvaluation evaluation = exchange.getProperty("FraudEvaluation", FraudEvaluation.class);
                PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);

                if (evaluation == null) {
                    evaluation = new FraudEvaluation(
                        message.paymentId(), message.amount(), message.customerId(),
                        100, FraudAction.REJECT,
                        "High risk", List.of()
                    );
                }

                FraudResult fraudResult = FraudResult.reject(
                    evaluation.paymentId(), evaluation.amount(), evaluation.customerId(),
                    evaluation.riskScore(), evaluation.reason(), evaluation.triggeredRules()
                );
                exchange.getIn().setBody(fraudResult);
            })
            .log("Rejecting payment ${body.paymentId}: high risk")
            .setHeader("kafka.KEY", simple("${body.paymentId}"))
            .marshal(fraudResultJson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .setBody(header("OriginalPaymentMessage"))
            .setHeader("kafka.KEY", simple("${body.paymentId}"))
            .marshal(paymentJson)
            .to("kafka:{{kafka.topic.payments.failed}}")
            .log("Published fraud rejection and failed event for: ${header.OriginalPaymentMessage.paymentId}");

        from("direct:fraud-review-queue")
            .routeId("fraud-review-queue")
            .process(exchange -> {
                FraudEvaluation evaluation = exchange.getProperty("FraudEvaluation", FraudEvaluation.class);
                PaymentMessage message = exchange.getIn().getBody(PaymentMessage.class);

                if (evaluation == null) {
                    evaluation = new FraudEvaluation(
                        message.paymentId(), message.amount(), message.customerId(),
                        60, FraudAction.REVIEW,
                        "Medium risk", List.of()
                    );
                }

                FraudResult fraudResult = FraudResult.review(
                    evaluation.paymentId(), evaluation.amount(), evaluation.customerId(),
                    evaluation.riskScore(), evaluation.reason(), evaluation.triggeredRules()
                );
                exchange.getIn().setBody(fraudResult);
            })
            .log("Sending payment ${body.paymentId} to fraud review queue")
            .setHeader("kafka.KEY", simple("${body.paymentId}"))
            .marshal(fraudResultJson)
            .to("kafka:{{kafka.topic.fraud.detected}}")
            .setBody(header("OriginalPaymentMessage"))
            .setHeader("kafka.KEY", simple("${body.paymentId}"))
            .marshal(paymentJson)
            .to("kafka:{{kafka.topic.payments.review}}")
            .log("Published fraud review event for: ${header.OriginalPaymentMessage.paymentId}");
    }
}
