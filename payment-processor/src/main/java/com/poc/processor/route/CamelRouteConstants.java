package com.poc.processor.route;

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
}
