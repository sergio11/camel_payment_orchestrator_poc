package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.config.ProviderConfig;
import com.poc.processor.processor.ProviderRouterBean;
import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;

@ApplicationScoped
public class ProviderSelectionRoute extends RouteBuilder {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ProviderConfig providerConfig;

    @Override
    public void configure() {
        JacksonDataFormat paymentJson = new JacksonDataFormat(objectMapper, PaymentMessage.class);
        JacksonDataFormat responseJson = new JacksonDataFormat(objectMapper, ProviderResponse.class);
        JacksonDataFormat fraudResultJson = new JacksonDataFormat(objectMapper, FraudResult.class);
        JacksonDataFormat stringJson = new JacksonDataFormat(objectMapper, Object.class);

        from("direct:provider-selection")
            .routeId("provider-selection")
            .log("Selecting provider for payment: ${header.CamelPaymentId}")
            .dynamicRouter(method(ProviderRouterBean.class, "routeToProvider"))
            .end();

        from("direct:provider-a")
            .routeId("provider-a")
            .onException(Exception.class)
                .handled(true)
                .maximumRedeliveries(providerConfig.maxRetries())
                .useExponentialBackOff()
                .backOffMultiplier(2)
                .maximumRedeliveryDelay(providerConfig.retryBackoff().toMillis())
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .log("Provider A failed after retries, falling back to Provider B: ${exception.message}")
                .to("direct:provider-b-fallback")
            .end()
            .log("Routing to Provider A: ${header.CamelPaymentId}")
            .circuitBreaker()
                .inheritErrorHandler(true)
                .resilience4jConfiguration()
                    .failureRateThreshold(providerConfig.circuitBreakerFailureThreshold())
                    .waitDurationInOpenState((int) providerConfig.circuitBreakerWaitDuration().toMillis())
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
                .to("direct:call-provider-a")
            .endCircuitBreaker()
            .log("Provider A completed for: ${header.CamelPaymentId}");

        from("direct:call-provider-a")
            .routeId("call-provider-a")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .process(exchange -> {
                exchange.getIn().setHeader("CamelFraudResult", exchange.getIn().getBody());
                exchange.getIn().setBody(exchange.getIn().getHeader("OriginalPaymentMessage"));
            })
            .marshal(paymentJson)
            .to("netty-http:{{provider.a-url}}")
            .unmarshal(responseJson)
            .choice()
                .when(simple("${body.success} == true"))
                    .log("Provider A success: ${body.transactionId}")
                    .process(exchange -> {
                        exchange.getIn().setBody(exchange.getIn().getHeader("CamelFraudResult"));
                    })
                    .marshal(fraudResultJson)
                    .to("kafka:{{kafka.topic.payments.processed}}")
                .otherwise()
                    .log("Provider A returned error: ${body.errorCode}")
                    .process(exchange -> {
                        ProviderResponse resp = exchange.getIn().getBody(ProviderResponse.class);
                        throw new RuntimeException("Provider A error: " + (resp != null ? resp.errorMessage() : "unknown"));
                    })
            .end();

        from("direct:provider-b-fallback")
            .routeId("provider-b-fallback")
            .onException(Exception.class)
                .handled(true)
                .maximumRedeliveries(providerConfig.maxRetries())
                .useExponentialBackOff()
                .backOffMultiplier(2)
                .maximumRedeliveryDelay(providerConfig.retryBackoff().toMillis())
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .log("Provider B also failed, sending to dead letter: ${exception.message}")
                .to("direct:dead-letter")
            .end()
            .log("Fallback to Provider B: ${header.CamelPaymentId}")
            .circuitBreaker()
                .inheritErrorHandler(true)
                .resilience4jConfiguration()
                    .failureRateThreshold(providerConfig.circuitBreakerFailureThreshold())
                    .waitDurationInOpenState((int) providerConfig.circuitBreakerWaitDuration().toMillis())
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
                .to("direct:call-provider-b")
            .endCircuitBreaker()
            .log("Provider B completed for: ${header.CamelPaymentId}");

        from("direct:call-provider-b")
            .routeId("call-provider-b")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .process(exchange -> {
                exchange.getIn().setHeader("CamelFraudResult", exchange.getIn().getBody());
                exchange.getIn().setBody(exchange.getIn().getHeader("OriginalPaymentMessage"));
            })
            .marshal(paymentJson)
            .to("netty-http:{{provider.b-url}}")
            .unmarshal(responseJson)
            .choice()
                .when(simple("${body.success} == true"))
                    .log("Provider B success: ${body.transactionId}")
                    .process(exchange -> {
                        exchange.getIn().setBody(exchange.getIn().getHeader("CamelFraudResult"));
                    })
                    .marshal(fraudResultJson)
                    .to("kafka:{{kafka.topic.payments.processed}}")
                .otherwise()
                    .log("Provider B returned error: ${body.errorCode}")
                    .process(exchange -> {
                        ProviderResponse resp = exchange.getIn().getBody(ProviderResponse.class);
                        throw new RuntimeException("Provider B error: " + (resp != null ? resp.errorMessage() : "unknown"));
                    })
            .end();

        from("direct:dead-letter")
            .routeId("dead-letter")
            .log("Sending to dead letter queue: ${header.CamelPaymentId}")
            .process(exchange -> {
                PaymentMessage orig = exchange.getIn().getHeader("OriginalPaymentMessage", PaymentMessage.class);
                if (orig != null) {
                    exchange.getIn().setBody(orig);
                }
            })
            .marshal(stringJson)
            .to("kafka:{{kafka.topic.dead.letter}}")
            .log("Published to dead letter topic");
    }
}
