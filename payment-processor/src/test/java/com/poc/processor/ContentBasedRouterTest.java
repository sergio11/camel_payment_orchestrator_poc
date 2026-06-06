package com.poc.processor;

import com.poc.processor.processor.ContentBasedRouterBean;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContentBasedRouterTest {

    @Inject
    ContentBasedRouterBean routerBean;

    @Test
    @DisplayName("3.41: Verify high amount (>10000) routes to fraud-review")
    void testHighAmountRoutesToFraudReview() {
        // Given: Amount > 10000
        String route = routerBean.routeToFraudCheck(15000.0, "CREDIT_CARD");
        
        // Then: Should route to fraud-review
        assertEquals("direct:fraud-review", route, "Amount > 10000 should route to fraud-review");
    }

    @Test
    @DisplayName("3.41: Verify amount exactly 10000 routes to fraud-check")
    void testAmountExactly10000RoutesToFraudCheck() {
        // Given: Amount == 10000 (not > 10000)
        String route = routerBean.routeToFraudCheck(10000.0, "CREDIT_CARD");
        
        // Then: Should route to fraud-check (not > 10000)
        assertEquals("direct:fraud-check", route, "Amount == 10000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.41: Verify amount 9999 routes to fraud-check")
    void testAmountUnder10000RoutesToFraudCheck() {
        // Given: Amount < 10000
        String route = routerBean.routeToFraudCheck(9999.0, "CREDIT_CARD");
        
        // Then: Should route to fraud-check
        assertEquals("direct:fraud-check", route, "Amount < 10000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify WALLET + amount > 5000 routes to fraud-review")
    void testWalletHighAmountRoutesToFraudReview() {
        // Given: WALLET + amount > 5000
        String route = routerBean.routeToFraudCheck(6000.0, "WALLET");
        
        // Then: Should route to fraud-review
        assertEquals("direct:fraud-review", route, "WALLET + amount > 5000 should route to fraud-review");
    }

    @Test
    @DisplayName("3.42: Verify WALLET + amount 5000 routes to fraud-check")
    void testWalletAmount5000RoutesToFraudCheck() {
        // Given: WALLET + amount == 5000 (not > 5000)
        String route = routerBean.routeToFraudCheck(5000.0, "WALLET");
        
        // Then: Should route to fraud-check
        assertEquals("direct:fraud-check", route, "WALLET + amount == 5000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify WALLET + amount 4999 routes to fraud-check")
    void testWalletAmountUnder5000RoutesToFraudCheck() {
        // Given: WALLET + amount < 5000
        String route = routerBean.routeToFraudCheck(4999.0, "WALLET");
        
        // Then: Should route to fraud-check
        assertEquals("direct:fraud-check", route, "WALLET + amount < 5000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify CREDIT_CARD + amount > 5000 routes to fraud-check (not WALLET)")
    void testCreditCardHighAmountRoutesToFraudCheck() {
        // Given: CREDIT_CARD + amount > 5000 (but not WALLET)
        String route = routerBean.routeToFraudCheck(6000.0, "CREDIT_CARD");
        
        // Then: Should route to fraud-check (only WALLET triggers fraud-review at > 5000)
        assertEquals("direct:fraud-check", route, "CREDIT_CARD + amount > 5000 should route to fraud-check");
    }

    @Test
    @DisplayName("3.42: Verify other payment methods route to fraud-check")
    void testOtherPaymentMethodsRouteToFraudCheck() {
        // Given: Other payment methods with high amount
        String routeDebit = routerBean.routeToFraudCheck(6000.0, "DEBIT_CARD");
        String routeTransfer = routerBean.routeToFraudCheck(6000.0, "BANK_TRANSFER");
        String routeCrypto = routerBean.routeToFraudCheck(6000.0, "CRYPTO");
        
        // Then: All should route to fraud-check
        assertEquals("direct:fraud-check", routeDebit);
        assertEquals("direct:fraud-check", routeTransfer);
        assertEquals("direct:fraud-check", routeCrypto);
    }
}