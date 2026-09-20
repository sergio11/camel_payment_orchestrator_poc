package com.poc.processor.application;

import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.port.inbound.RouteFraudUseCase.FraudRoutingDecision;
import com.poc.processor.port.outbound.RoutingDecisionPort;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudRoutingServiceUnitTest {

    @Mock
    RoutingDecisionPort routingPort;

    @InjectMocks
    FraudRoutingService service;

    @Test
    @DisplayName("route() should return FraudRoutingDecision with correct action and target")
    void testRoute() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation evaluation = new FraudEvaluation(
            "pay-1", msg.amount(), msg.customerId(),
            30, FraudAction.APPROVE, "Low risk", List.of("LOW_AMOUNT")
        );

        when(routingPort.resolveFraudRoute(msg.amount(), msg.paymentMethod(), msg.country()))
            .thenReturn("direct:fraud-check");

        FraudRoutingDecision result = service.route(msg, evaluation);

        assertEquals(FraudAction.APPROVE, result.action());
        assertEquals("direct:fraud-check", result.routeTarget());
        assertEquals(evaluation, result.evaluation());
        verify(routingPort).resolveFraudRoute(msg.amount(), msg.paymentMethod(), msg.country());
    }

    @Test
    @DisplayName("route() should use routing port to resolve route target")
    void testRoute_usesRoutingPort() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "pay-2", new BigDecimal("20000.00"), "USD", "cust-2",
            "CREDIT_CARD", "XX", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation evaluation = new FraudEvaluation(
            "pay-2", msg.amount(), msg.customerId(),
            85, FraudAction.REJECT, "High risk", List.of("HIGH_AMOUNT")
        );

        when(routingPort.resolveFraudRoute(msg.amount(), msg.paymentMethod(), msg.country()))
            .thenReturn("direct:fraud-review");

        FraudRoutingDecision result = service.route(msg, evaluation);

        assertEquals(FraudAction.REJECT, result.action());
        assertEquals("direct:fraud-review", result.routeTarget());
    }
}
