package com.poc.processor.route;

import com.poc.processor.processor.ProviderRouterBean;
import com.poc.shared.event.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@ApplicationScoped
public class ProviderSelectionRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:provider-selection")
            .routeId("provider-selection")
            .log("Selecting provider for payment: ${header.CamelPaymentId}")
            .dynamicRouter(method(ProviderRouterBean.class, "routeToProvider"))
            .end();

        from("direct:provider-a")
            .routeId("provider-a")
            .log("Routing to Provider A: ${header.CamelPaymentId}")
            .circuitBreaker()
                .resilience4jConfiguration("providerA")
                .to("direct:call-provider-a")
            .endCircuitBreaker()
            .retry()
                .exponentialBackoff(1000, 2, 5000)
                .maximumRedeliveries(5)
            .endRetry()
            .onException(Exception.class)
                .handled(true)
                .log("Provider A failed after retries, falling back to Provider B: ${exception.message}")
                .to("direct:provider-b-fallback")
            .end()
            .log("Provider A completed for: ${header.CamelPaymentId}");

        from("direct:call-provider-a")
            .routeId("call-provider-a")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .marshal().json(JsonLibrary.Jackson)
            .to("netty-http:{{provider.a-url}}")
            .unmarshal().json(JsonLibrary.Jackson, ProviderResponse.class)
            .choice()
                .when(simple("${body.success} == true"))
                    .log("Provider A success: ${body.transactionId}")
                .otherwise()
                    .log("Provider A returned error: ${body.errorCode}")
                    .throwException(new RuntimeException("Provider A error: ${body.errorMessage}"))
            .end();

        from("direct:provider-b-fallback")
            .routeId("provider-b-fallback")
            .log("Fallback to Provider B: ${header.CamelPaymentId}")
            .circuitBreaker()
                .resilience4jConfiguration("providerB")
                .to("direct:call-provider-b")
            .endCircuitBreaker()
            .retry()
                .exponentialBackoff(1000, 2, 5000)
                .maximumRedeliveries(5)
            .endRetry()
            .onException(Exception.class)
                .handled(true)
                .log("Provider B also failed, sending to dead letter: ${exception.message}")
                .to("direct:dead-letter")
            .end()
            .log("Provider B completed for: ${header.CamelPaymentId}");

        from("direct:call-provider-b")
            .routeId("call-provider-b")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .marshal().json(JsonLibrary.Jackson)
            .to("netty-http:{{provider.b-url}}")
            .unmarshal().json(JsonLibrary.Jackson, ProviderResponse.class)
            .choice()
                .when(simple("${body.success} == true"))
                    .log("Provider B success: ${body.transactionId}")
                .otherwise()
                    .log("Provider B returned error: ${body.errorCode}")
                    .throwException(new RuntimeException("Provider B error: ${body.errorMessage}"))
            .end();

        from("direct:dead-letter")
            .routeId("dead-letter")
            .log("Sending to dead letter queue: ${header.CamelPaymentId}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.dead.letter}}")
            .log("Published to dead letter topic");
    }
}