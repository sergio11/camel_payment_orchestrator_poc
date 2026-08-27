package com.poc.processor;

import com.poc.processor.processor.ContentBasedRouterBean;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContentBasedRouterTest {

    @Inject
    ContentBasedRouterBean routerBean;

    @Test
    @DisplayName("3.41: Verify high amount (>15000) routes to fraud-review")
    void testHighAmountRoutesToFraudReview() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("15001"), "CREDIT_CARD", "US");
        assertEquals("direct:fraud-review", route, "Amount > 15000 should route to fraud-review");
    }

    @Test
    @DisplayName("3.41: Verify amount exactly 15000 routes to fraud-check")
    void testAmountExactly15000RoutesToFraudCheck() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("15000"), "CREDIT_CARD", "US");
        assertEquals("direct:fraud-check", route, "Amount == 15000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.41: Verify amount 14999 routes to fraud-check")
    void testAmountUnder15000RoutesToFraudCheck() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("14999"), "CREDIT_CARD", "US");
        assertEquals("direct:fraud-check", route, "Amount < 15000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify WALLET + amount > 5000 routes to fraud-review")
    void testWalletHighAmountRoutesToFraudReview() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("6000"), "WALLET", "US");
        assertEquals("direct:fraud-review", route, "WALLET + amount > 5000 should route to fraud-review");
    }

    @Test
    @DisplayName("3.42: Verify WALLET + amount 5000 routes to fraud-check")
    void testWalletAmount5000RoutesToFraudCheck() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("5000"), "WALLET", "US");
        assertEquals("direct:fraud-check", route, "WALLET + amount == 5000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify WALLET + amount 4999 routes to fraud-check")
    void testWalletAmountUnder5000RoutesToFraudCheck() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("4999"), "WALLET", "US");
        assertEquals("direct:fraud-check", route, "WALLET + amount < 5000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify CREDIT_CARD + amount > 5000 routes to fraud-check (not WALLET)")
    void testCreditCardHighAmountRoutesToFraudCheck() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("6000"), "CREDIT_CARD", "US");
        assertEquals("direct:fraud-check", route, "CREDIT_CARD + amount > 5000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify other payment methods route to fraud-check")
    void testOtherPaymentMethodsRouteToFraudCheck() {
        String routeDebit = routerBean.routeToFraudCheck(new BigDecimal("6000"), "DEBIT_CARD", "US");
        String routeTransfer = routerBean.routeToFraudCheck(new BigDecimal("6000"), "BANK_TRANSFER", "US");
        String routeCrypto = routerBean.routeToFraudCheck(new BigDecimal("6000"), "CRYPTO", "US");
        assertEquals("direct:fraud-check", routeDebit);
        assertEquals("direct:fraud-check", routeTransfer);
        assertEquals("direct:fraud-check", routeCrypto);
    }

    @Test
    @DisplayName("Verify high-risk country XX routes to fraud-review")
    void testHighRiskCountryXXRoutesToFraudReview() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("1000"), "CREDIT_CARD", "XX");
        assertEquals("direct:fraud-review", route, "High-risk country XX should route to fraud-review");
    }

    @Test
    @DisplayName("Verify high-risk country YY routes to fraud-review")
    void testHighRiskCountryYYRoutesToFraudReview() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("1000"), "CREDIT_CARD", "YY");
        assertEquals("direct:fraud-review", route, "High-risk country YY should route to fraud-review");
    }

    @Test
    @DisplayName("Verify non-high-risk country routes to fraud-check")
    void testNonHighRiskCountryRoutesToFraudCheck() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("1000"), "CREDIT_CARD", "US");
        assertEquals("direct:fraud-check", route, "Non-high-risk country should route to fraud-check");
    }

    @Test
    @DisplayName("Verify high-risk country still routes to fraud-review even with low amount")
    void testHighRiskCountryLowAmountRoutesToReview() {
        String route = routerBean.routeToFraudCheck(new BigDecimal("100"), "CREDIT_CARD", "XX");
        assertEquals("direct:fraud-review", route, "High-risk country should override low amount");
    }
}
