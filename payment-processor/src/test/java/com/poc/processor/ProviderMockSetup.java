package com.poc.processor;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWith;

@ApplicationScoped
public class ProviderMockSetup {

    @Inject
    CamelContext camelContext;

    @Inject
    ProviderMockConfig mockConfig;

    static volatile int providerAInterceptCount = 0;
    static volatile int providerBInterceptCount = 0;

    void onStartup(@Observes StartupEvent event) throws Exception {
        AdviceWith.adviceWith(camelContext, "call-provider-a", builder -> {
            builder.weaveByToUri("netty-http:*")
                .replace()
                .process(exchange -> {
                    providerAInterceptCount++;
                    mockConfig.incrementProviderACallCount();
                    String response = mockConfig.isProviderASucceeds()
                        ? mockConfig.providerSuccessResponse("provider-a")
                        : mockConfig.providerAFailureResponse();
                    exchange.getMessage().setBody(response, String.class);
                });
        });
        AdviceWith.adviceWith(camelContext, "call-provider-b", builder -> {
            builder.weaveByToUri("netty-http:*")
                .replace()
                .process(exchange -> {
                    providerBInterceptCount++;
                    String response = mockConfig.isProviderBSucceeds()
                        ? mockConfig.providerBSuccessResponse()
                        : mockConfig.providerBFailureResponse();
                    exchange.getMessage().setBody(response, String.class);
                });
        });
    }
}
