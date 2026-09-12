package com.poc.processor.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;

@ApplicationScoped
@Path("/provider-a")
public class ProviderAService extends AbstractMockProviderService {

    public ProviderAService() {
        super("provider-a", 0.10, 100, 200);
    }
}
