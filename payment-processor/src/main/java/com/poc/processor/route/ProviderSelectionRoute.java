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
        // Dynamic router bean for provider selection
        from("direct:provider-selection")
            .routeId("provider-selection")
            .log("Selecting provider for payment: ${header.CamelPaymentId}")
            .dynamicRouter(method(ProviderRouterBean.class, "routeToProvider"))
            .end();

        // Provider A - Circuit Breaker handled by Resilience4j in ProviderRouterBean
        from("direct:provider-a")
            .routeId("provider-a")
            .log("Routing to Provider A: ${header.CamelPaymentId}")
            .bean(ProviderRouterBean.class, "callProviderA(${body})")
            .onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .backOffMultiplier(2)
                .handled(true)
                .log("Provider A failed after retries, falling back to Provider B: ${exception.message}")
                .to("direct:provider-b-fallback")
            .end()
            .log("Provider A completed for: ${header.CamelPaymentId}");

        // Provider B fallback
        from("direct:provider-b-fallback")
            .routeId("provider-b-fallback")
            .log("Fallback to Provider B: ${header.CamelPaymentId}")
            .bean(ProviderRouterBean.class, "callProviderB(${body})")
            .onException(Exception.class)
                .handled(true)
                .log("Provider B also failed, sending to dead letter: ${exception.message}")
                .to("direct:dead-letter")
            .end()
            .log("Provider B completed for: ${header.CamelPaymentId}");

        // Dead Letter Channel
        from("direct:dead-letter")
            .routeId("dead-letter")
            .log("Sending to dead letter queue: ${header.CamelPaymentId}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:{{kafka.topic.dead.letter}}")
            .log("Published to dead letter topic");
    }
}