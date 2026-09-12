package com.poc.processor.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;

@ApplicationScoped
@Path("/provider-b")
public class ProviderBService extends AbstractMockProviderService {

    public ProviderBService() {
        super("provider-b", 0.02, 500, 1000);
    }
}
