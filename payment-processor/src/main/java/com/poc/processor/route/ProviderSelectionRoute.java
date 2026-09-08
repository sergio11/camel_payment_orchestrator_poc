package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.config.ProviderConfig;
import com.poc.processor.processor.ProviderRouterBean;
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
        JacksonDataFormat stringJson = new JacksonDataFormat(objectMapper, Object.class);

        from("direct:provider-selection")
            .routeId("provider-selection")
            .bean(ProviderRouterBean.class, "restoreOriginalHeaders")
            .log("Selecting provider for payment: ${header.OriginalPaymentId}")
            .dynamicRouter(method(ProviderRouterBean.class, "routeToProvider"))
            .end();

        from("direct:provider-a")
            .routeId("provider-a")
            .log("Routing to Provider A: ${header.OriginalPaymentId}")
            .circuitBreaker()
                .resilience4jConfiguration()
                    .failureRateThreshold(providerConfig.circuitBreakerFailureThreshold())
                    .waitDurationInOpenState((int) Math.max(1, providerConfig.circuitBreakerWaitDuration().toSeconds()))
                    .slidingWindowSize(providerConfig.circuitBreakerSlidingWindowSize())
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
                .to("direct:call-provider-a")
            .onFallback()
                .log("Provider A failed, falling back to Provider B: ${header.OriginalPaymentId}")
                .to("direct:provider-b-fallback")
            .end()
            .log("Provider A completed for: ${header.OriginalPaymentId}");

        from("direct:call-provider-a")
            .routeId("call-provider-a")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .bean(ProviderRouterBean.class, "prepareProviderCall")
            .choice()
                .when(header("OriginalPaymentMessage").isNull())
                    .log("Missing OriginalPaymentMessage, routing to dead letter")
                    .to("direct:dead-letter")
                    .stop()
                .otherwise()
                    .marshal(paymentJson)
                    .to("netty-http:{{provider.a-url}}")
                    .unmarshal(responseJson)
                    .choice()
                        .when(simple("${body.success} == true"))
                            .log("Provider A success: ${body.transactionId}")
                            .setHeader("kafka.KEY", header("OriginalPaymentId"))
                            .log("Publishing processed event for payment: ${header.kafka.KEY}")
                            .marshal(responseJson)
                            .to("kafka:{{kafka.topic.payments.processed}}")
                        .otherwise()
                            .log("Provider A returned error: ${body.errorCode}")
                            .process(exchange -> {
                                ProviderResponse resp = exchange.getMessage().getBody(ProviderResponse.class);
                                throw new RuntimeException("Provider A error: " + resp.errorMessage());
                            })
                    .end()
            .end();

        from("direct:provider-b-fallback")
            .routeId("provider-b-fallback")
            .log("Fallback to Provider B: ${header.OriginalPaymentId}")
            .circuitBreaker()
                .resilience4jConfiguration()
                    .failureRateThreshold(providerConfig.circuitBreakerFailureThreshold())
                    .waitDurationInOpenState((int) Math.max(1, providerConfig.circuitBreakerWaitDuration().toSeconds()))
                    .slidingWindowSize(providerConfig.circuitBreakerSlidingWindowSize())
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
                .to("direct:call-provider-b")
            .onFallback()
                .log("Provider B also failed, sending to dead letter: ${header.OriginalPaymentId}")
                .to("direct:dead-letter")
            .end()
            .log("Provider B completed for: ${header.OriginalPaymentId}");

        from("direct:call-provider-b")
            .routeId("call-provider-b")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .bean(ProviderRouterBean.class, "prepareProviderCall")
            .choice()
                .when(header("OriginalPaymentMessage").isNull())
                    .log("Missing OriginalPaymentMessage, routing to dead letter")
                    .to("direct:dead-letter")
                    .stop()
                .otherwise()
                    .marshal(paymentJson)
                    .to("netty-http:{{provider.b-url}}")
                    .unmarshal(responseJson)
                    .choice()
                        .when(simple("${body.success} == true"))
                            .log("Provider B success: ${body.transactionId}")
                            .setHeader("kafka.KEY", header("OriginalPaymentId"))
                            .log("Publishing processed event for payment: ${header.kafka.KEY}")
                            .marshal(responseJson)
                            .to("kafka:{{kafka.topic.payments.processed}}")
                        .otherwise()
                            .log("Provider B returned error: ${body.errorCode}")
                            .process(exchange -> {
                                ProviderResponse resp = exchange.getMessage().getBody(ProviderResponse.class);
                                throw new RuntimeException("Provider B error: " + resp.errorMessage());
                            })
                    .end()
            .end();

        from("direct:dead-letter")
            .routeId("dead-letter")
            .bean(ProviderRouterBean.class, "restoreDeadLetter")
            .choice()
                .when(simple("${body} == null"))
                    .log("Missing body and OriginalPaymentMessage, dropping message: ${header.OriginalPaymentId}")
                    .stop()
                .otherwise()
                    .log("Sending to dead letter queue: ${header.OriginalPaymentId}")
                    .setHeader("kafka.KEY", header("OriginalPaymentId"))
                    .marshal(stringJson)
                    .to("kafka:{{kafka.topic.dead.letter}}")
                    .log("Published to dead letter topic")
            .end();
    }
}
