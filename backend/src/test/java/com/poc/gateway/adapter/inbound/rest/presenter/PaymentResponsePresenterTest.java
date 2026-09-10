package com.poc.gateway.adapter.inbound.rest.presenter;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentResponsePresenterTest {

    @Test
    void buildNotFound_returns404() {
        Response r = PaymentResponsePresenter.buildNotFound("not found");
        assertEquals(404, r.getStatus());
        assertNotNull(r.getEntity());
    }

    @Test
    void buildNotFound_entityIsErrorDTO() {
        Response r = PaymentResponsePresenter.buildNotFound("pay-1 not found");
        assertTrue(r.getEntity().toString().contains("pay-1 not found"));
    }

    @Test
    void buildBadRequest_returns400() {
        Response r = PaymentResponsePresenter.buildBadRequest("bad");
        assertEquals(400, r.getStatus());
        assertNotNull(r.getEntity());
    }

    @Test
    void buildBadRequest_entityIsErrorDTO() {
        Response r = PaymentResponsePresenter.buildBadRequest("invalid amount");
        assertTrue(r.getEntity().toString().contains("invalid amount"));
    }

    @Test
    void buildConflict_returns409() {
        Response r = PaymentResponsePresenter.buildConflict("conflict");
        assertEquals(409, r.getStatus());
        assertNotNull(r.getEntity());
    }

    @Test
    void buildConflict_entityIsErrorDTO() {
        Response r = PaymentResponsePresenter.buildConflict("duplicate idempotency key");
        assertTrue(r.getEntity().toString().contains("duplicate idempotency key"));
    }

    @Test
    void buildInternalError_returns500() {
        Response r = PaymentResponsePresenter.buildInternalError("error");
        assertEquals(500, r.getStatus());
        assertNotNull(r.getEntity());
    }

    @Test
    void buildInternalError_entityIsErrorDTO() {
        Response r = PaymentResponsePresenter.buildInternalError("database connection failed");
        assertTrue(r.getEntity().toString().contains("database connection failed"));
    }
}
