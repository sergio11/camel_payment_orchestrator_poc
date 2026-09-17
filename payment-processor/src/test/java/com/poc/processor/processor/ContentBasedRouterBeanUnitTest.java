package com.poc.processor.processor;

import com.poc.processor.config.FraudRulesConfig;
import com.poc.processor.route.CamelRouteConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentBasedRouterBeanUnitTest {

    @Mock
    FraudRulesConfig config;

    @InjectMocks
    ContentBasedRouterBean bean;

    @BeforeEach
    void setUp() {
        lenient().when(config.cbrHighAmountThreshold()).thenReturn(new BigDecimal("10000"));
        lenient().when(config.cbrWalletAmountThreshold()).thenReturn(new BigDecimal("500"));
        lenient().when(config.highRiskCountries()).thenReturn(List.of("XX", "YY"));
    }

    @Test
    @DisplayName("resolveFraudRoute delegates to routeToFraudCheck")
    void testResolveFraudRoute() {
        when(config.cbrHighAmountThreshold()).thenReturn(new BigDecimal("10000"));
        String result = bean.resolveFraudRoute(new BigDecimal("100"), "CARD", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-check for null amount")
    void testNullAmount() {
        String result = bean.routeToFraudCheck(null, "CARD", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-review for high amount")
    void testHighAmount() {
        String result = bean.routeToFraudCheck(new BigDecimal("15000"), "CARD", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_REVIEW, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-review for high-risk country")
    void testHighRiskCountry() {
        String result = bean.routeToFraudCheck(new BigDecimal("100"), "CARD", "XX");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_REVIEW, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-review for WALLET above threshold")
    void testWalletAboveThreshold() {
        String result = bean.routeToFraudCheck(new BigDecimal("600"), "WALLET", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_REVIEW, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-check for WALLET below threshold")
    void testWalletBelowThreshold() {
        String result = bean.routeToFraudCheck(new BigDecimal("400"), "WALLET", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-check for normal payment")
    void testNormalPayment() {
        String result = bean.routeToFraudCheck(new BigDecimal("100"), "CARD", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-check for null country")
    void testNullCountry() {
        String result = bean.routeToFraudCheck(new BigDecimal("100"), "CARD", null);
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-check for non-high-risk country")
    void testNonHighRiskCountry() {
        String result = bean.routeToFraudCheck(new BigDecimal("100"), "CARD", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }

    @Test
    @DisplayName("routeToFraudCheck returns fraud-review for WALLET at exact threshold")
    void testWalletAtThreshold() {
        when(config.cbrWalletAmountThreshold()).thenReturn(new BigDecimal("500"));
        String result = bean.routeToFraudCheck(new BigDecimal("500"), "WALLET", "US");
        assertEquals(CamelRouteConstants.DIRECT_FRAUD_CHECK, result);
    }
}
