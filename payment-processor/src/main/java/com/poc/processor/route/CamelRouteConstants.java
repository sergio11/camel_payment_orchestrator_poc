package com.poc.processor.route;

import org.apache.camel.Exchange;

public final class CamelRouteConstants {

    private CamelRouteConstants() {}

    public static final String DIRECT_FRAUD_CHECK = "direct:fraud-check";
    public static final String DIRECT_FRAUD_REVIEW = "direct:fraud-review";

    public static final String RETRY_TOPIC_URI = "kafka:{{kafka.topic.payments.retry}}";
    public static final String DEAD_LETTER_TOPIC_URI = "kafka:{{kafka.topic.dead.letter}}";
    public static final String DIRECT_RETRY_HANDLER = "direct:retry-handler";
    public static final String DIRECT_POISON_DLQ = "direct:poison-dlq";
    public static final String DIRECT_DLQ_HANDLER = "direct:dlq-handler";
    public static final int MAX_RETRY_ATTEMPTS = 3;

    public static final String HEADER_ORIGINAL_PAYMENT_ID = "OriginalPaymentId";
    public static final String HEADER_ORIGINAL_EVENT_ID = "OriginalEventId";
    public static final String HEADER_ORIGINAL_PAYMENT_MESSAGE = "OriginalPaymentMessage";
    public static final String HEADER_KAFKA_KEY = "kafka.KEY";
    public static final String HEADER_FRAUD_ACTION = "CamelFraudAction";
    public static final String HEADER_RISK_SCORE = "CamelRiskScore";
    public static final String HEADER_FRAUD_ROUTE_TARGET = "FraudRouteTarget";
    public static final String HEADER_RETRY_COUNT = "retryCount";

    public static void setHeaderIfAbsent(Exchange exchange, String header, Object value) {
        if (exchange.getMessage().getHeader(header) == null) {
            exchange.getMessage().setHeader(header, value);
        }
    }
}
