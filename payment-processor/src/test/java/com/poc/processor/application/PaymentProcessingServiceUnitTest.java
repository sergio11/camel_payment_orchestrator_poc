package com.poc.processor.application;

import com.poc.processor.domain.FraudAction;
import com.poc.processor.domain.FraudEvaluation;
import com.poc.processor.domain.exception.PaymentProcessingException;
import com.poc.processor.port.inbound.EnrichPaymentUseCase;
import com.poc.processor.port.inbound.EvaluateFraudUseCase;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceUnitTest {

    @Mock
    EnrichPaymentUseCase enrichPaymentUseCase;

    @Mock
    EvaluateFraudUseCase evaluateFraudUseCase;

    @InjectMocks
    PaymentProcessingService service;

    @Test
    @DisplayName("execute calls enrich then evaluate in order")
    void execute_happyPath() {
        PaymentMessage msg = createValidMessage();
        PaymentMessage enriched = createValidMessage();
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", BigDecimal.TEN, "cust-1", 10, FraudAction.APPROVE, null, List.of()
        );

        when(enrichPaymentUseCase.enrich(any(PaymentMessage.class))).thenReturn(enriched);
        when(evaluateFraudUseCase.evaluate(any(PaymentMessage.class))).thenReturn(eval);

        service.execute(msg);

        verify(enrichPaymentUseCase, times(1)).enrich(msg);
        verify(evaluateFraudUseCase, times(1)).evaluate(enriched);
    }

    @Test
    @DisplayName("execute propagates enrichment result to fraud evaluation")
    void execute_passesEnrichedMessageToEvaluate() {
        PaymentMessage msg = createValidMessage();
        PaymentMessage enriched = new PaymentMessage(
            "evt-2", "pay-1", new BigDecimal("500"), "EUR", "cust-2",
            "WALLET", "DE", 5, true, 60, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        FraudEvaluation eval = new FraudEvaluation(
            "pay-1", new BigDecimal("500"), "cust-2", 60, FraudAction.REVIEW, "Medium risk", List.of("HIGH_AMOUNT")
        );

        when(enrichPaymentUseCase.enrich(any())).thenReturn(enriched);
        when(evaluateFraudUseCase.evaluate(any())).thenReturn(eval);

        service.execute(msg);

        verify(evaluateFraudUseCase).evaluate(enriched);
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null paymentId")
    void validate_nullPaymentId() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", null, BigDecimal.TEN, "USD", "c1",
            "CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null amount")
    void validate_nullAmount() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "p1", null, "USD", "c1",
            "CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null currency")
    void validate_nullCurrency() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "p1", BigDecimal.TEN, null, "c1",
            "CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null customerId")
    void validate_nullCustomerId() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "p1", BigDecimal.TEN, "USD", null,
            "CARD", "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage throws for null paymentMethod")
    void validate_nullPaymentMethod() {
        PaymentMessage msg = new PaymentMessage(
            "evt-1", "p1", BigDecimal.TEN, "USD", "c1",
            null, "US", 0, false, 0, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        assertThrows(PaymentProcessingException.class, () ->
            PaymentProcessingService.validatePaymentMessage(msg));
    }

    @Test
    @DisplayName("validatePaymentMessage passes for valid message")
    void validate_validMessage() {
        assertDoesNotThrow(() ->
            PaymentProcessingService.validatePaymentMessage(createValidMessage()));
    }

    private PaymentMessage createValidMessage() {
        return new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }
}
