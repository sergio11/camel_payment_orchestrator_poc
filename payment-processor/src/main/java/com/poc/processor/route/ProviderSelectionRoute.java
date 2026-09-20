package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.config.ProviderConfig;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;

@ApplicationScoped
public class ProviderSelectionRoute extends RouteBuilder {

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private ProviderConfig providerConfig;

    public static void failOnProviderError(org.apache.camel.Exchange exchange, String providerName) {
        ProviderResponse resp = exchange.getMessage().getBody(ProviderResponse.class);
        throw new RuntimeException(providerName + " error: " + resp.errorMessage());
    }

    @Override
    public void configure() {
        JacksonDataFormat paymentJson = new JacksonDataFormat(objectMapper, PaymentMessage.class);
        JacksonDataFormat responseJson = new JacksonDataFormat(objectMapper, ProviderResponse.class);
        JacksonDataFormat stringJson = new JacksonDataFormat(objectMapper, Object.class);

        from("direct:provider-selection")
            .routeId("provider-selection")
            .bean("providerRouterBean", "restoreOriginalHeaders")
            .log("Selecting provider for payment: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
            .dynamicRouter(method("providerRouterBean", "routeToProvider"))
            .end();

        from("direct:provider-a")
            .routeId("provider-a")
            .log("Routing to Provider A: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
            .circuitBreaker()
                .resilience4jConfiguration()
                    .failureRateThreshold(providerConfig.circuitBreakerFailureThreshold())
                    .waitDurationInOpenState((int) Math.max(1, providerConfig.circuitBreakerWaitDuration().toSeconds()))
                    .slidingWindowSize(providerConfig.circuitBreakerSlidingWindowSize())
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
                .to("direct:call-provider-a")
            .onFallback()
                .log("Provider A failed, falling back to Provider B: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
                .to("direct:provider-b-fallback")
            .end()
            .log("Provider A completed for: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}");

        from("direct:call-provider-a")
            .routeId("call-provider-a")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .bean("providerRouterBean", "prepareProviderCall")
            .choice()
                .when(header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE).isNull())
                    .log("Missing OriginalPaymentMessage, routing to dead letter")
                    .to("direct:dead-letter")
                    .stop()
                .otherwise()
                    .marshal(paymentJson)
                    .to("netty-http:{{provider.a-url}}?connectTimeout=5000&socketTimeout=10000")
                    .bean("providerRouterBean", "restoreHeadersAfterHttpCall")
                    .unmarshal(responseJson)
                    .choice()
                        .when(simple("${body.success} == true"))
                            .log("Provider A success: ${body.transactionId}")
                            .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID))
                            .log("Publishing processed event for payment: ${header." + CamelRouteConstants.HEADER_KAFKA_KEY + "}")
                            .marshal(responseJson)
                            .to("kafka:{{kafka.topic.payments.processed}}")
                        .otherwise()
                            .log("Provider A returned error: ${body.errorCode}")
                            .process(exchange -> failOnProviderError(exchange, "Provider A"))
                    .end()
            .end();

        from("direct:provider-b-fallback")
            .routeId("provider-b-fallback")
            .log("Fallback to Provider B: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
            .circuitBreaker()
                .resilience4jConfiguration()
                    .failureRateThreshold(providerConfig.circuitBreakerFailureThreshold())
                    .waitDurationInOpenState((int) Math.max(1, providerConfig.circuitBreakerWaitDuration().toSeconds()))
                    .slidingWindowSize(providerConfig.circuitBreakerSlidingWindowSize())
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
                .to("direct:call-provider-b")
            .onFallback()
                .log("Provider B also failed, sending to dead letter: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
                .to("direct:dead-letter")
            .end()
            .log("Provider B completed for: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}");

        from("direct:call-provider-b")
            .routeId("call-provider-b")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .bean("providerRouterBean", "prepareProviderCall")
            .choice()
                .when(header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_MESSAGE).isNull())
                    .log("Missing OriginalPaymentMessage, routing to dead letter")
                    .to("direct:dead-letter")
                    .stop()
                .otherwise()
                    .marshal(paymentJson)
                    .to("netty-http:{{provider.b-url}}?connectTimeout=5000&socketTimeout=10000")
                    .bean("providerRouterBean", "restoreHeadersAfterHttpCall")
                    .unmarshal(responseJson)
                    .choice()
                        .when(simple("${body.success} == true"))
                            .log("Provider B success: ${body.transactionId}")
                            .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID))
                            .log("Publishing processed event for payment: ${header." + CamelRouteConstants.HEADER_KAFKA_KEY + "}")
                            .marshal(responseJson)
                            .to("kafka:{{kafka.topic.payments.processed}}")
                        .otherwise()
                            .log("Provider B returned error: ${body.errorCode}")
                            .process(exchange -> failOnProviderError(exchange, "Provider B"))
                    .end()
            .end();

        from("direct:dead-letter")
            .routeId("dead-letter")
            .bean("providerRouterBean", "restoreDeadLetter")
            .choice()
                .when(simple("${body} == null"))
                    .log("Missing body and OriginalPaymentMessage, dropping message: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
                    .stop()
                .otherwise()
                    .log("Sending to dead letter queue: ${header." + CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID + "}")
                    .setHeader(CamelRouteConstants.HEADER_KAFKA_KEY, header(CamelRouteConstants.HEADER_ORIGINAL_PAYMENT_ID))
                    .marshal(stringJson)
                    .to("kafka:{{kafka.topic.dead.letter}}")
                    .log("Published to dead letter topic")
            .end();
    }
}
