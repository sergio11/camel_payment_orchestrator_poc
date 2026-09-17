package com.poc.gateway.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class JacksonPaymentEventSerializer implements PaymentEventSerializer {

    private static final Logger LOG = Logger.getLogger(JacksonPaymentEventSerializer.class);

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public String serialize(Payment payment) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "paymentId", payment.id().toString(),
                "amount", payment.amount() != null ? payment.amount().toString() : "0",
                "currency", String.valueOf(payment.currency()),
                "customerId", String.valueOf(payment.customerId()),
                "paymentMethod", String.valueOf(payment.paymentMethod()),
                "country", String.valueOf(payment.country()),
                "metadata", payment.metadata() != null ? payment.metadata() : Map.of()
            ));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to serialize payment %s, using minimal payload", payment.id());
            return "{\"paymentId\":\"" + payment.id() + "\"}";
        }
    }
}
