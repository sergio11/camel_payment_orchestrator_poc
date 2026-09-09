package com.poc.gateway.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionMapperBranchesTest {

    private GlobalExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GlobalExceptionMapper();
    }

    @Test
    @DisplayName("isBadRequest returns false when exception has no cause")
    void isBadRequest_noCause_returnsFalse() {
        assertFalse(mapper.isBadRequest(new RuntimeException("plain")));
    }

    @Test
    @DisplayName("isBadRequest returns false when cause does not match markers")
    void isBadRequest_causeNoMatch_returnsFalse() {
        RuntimeException cause = new RuntimeException("some other issue");
        RuntimeException wrapper = new RuntimeException("wrapper", cause);
        assertFalse(mapper.isBadRequest(wrapper));
    }

    @Test
    @DisplayName("isConstraintViolation with cause containing constraint keyword returns true")
    void isConstraintViolation_causeWithConstraintKeyword_returnsTrue() {
        RuntimeException cause = new RuntimeException("duplicate key value violates unique constraint");
        RuntimeException wrapper = new RuntimeException("wrapper", cause);
        assertTrue(mapper.isConstraintViolation(wrapper));
    }

    @Test
    @DisplayName("isConstraintViolation with cause with null message returns false")
    void isConstraintViolation_causeNullMessage_returnsFalse() {
        RuntimeException cause = new RuntimeException((String) null);
        RuntimeException wrapper = new RuntimeException("wrapper", cause);
        assertFalse(mapper.isConstraintViolation(wrapper));
    }

    @Test
    @DisplayName("isConstraintViolation with uq_ keyword in message returns true")
    void isConstraintViolation_uqUnderscore_returnsTrue() {
        RuntimeException ex = new RuntimeException("violates uq_payments_idx");
        assertTrue(mapper.isConstraintViolation(ex));
    }

    @Test
    @DisplayName("isConstraintViolation with uq space keyword in message returns true")
    void isConstraintViolation_uqSpace_returnsTrue() {
        RuntimeException ex = new RuntimeException("violates uq payments idx");
        assertTrue(mapper.isConstraintViolation(ex));
    }

    @Test
    @DisplayName("toResponse with non-Jackson bad request exception returns 400 with body error")
    void toResponse_badRequestWithMessage_returns400() {
        FakeJsonMappingException ex = new FakeJsonMappingException("unexpected token");
        Response response = mapper.toResponse(ex);
        assertEquals(400, response.getStatus());
    }

    @Test
    @DisplayName("matchesAny with null text returns false")
    void matchesAny_nullText_returnsFalse() {
        assertFalse(GlobalExceptionMapper.matchesAny(null, java.util.List.of("marker")));
    }

    @Test
    @DisplayName("matchesAny with null markers returns false")
    void matchesAny_nullMarkers_returnsFalse() {
        assertFalse(GlobalExceptionMapper.matchesAny("text", null));
    }

    @Test
    @DisplayName("isConstraintViolation with null message returns false")
    void isConstraintViolation_nullMessage_returnsFalse() {
        RuntimeException ex = new RuntimeException((String) null);
        assertFalse(mapper.isConstraintViolation(ex));
    }

    @Test
    @DisplayName("toResponse with WebApplicationException returns its response")
    void toResponse_webApplicationException_returnsItsResponse() {
        jakarta.ws.rs.WebApplicationException ex =
            new jakarta.ws.rs.WebApplicationException("not found", jakarta.ws.rs.core.Response.Status.NOT_FOUND);
        Response response = mapper.toResponse(ex);
        assertEquals(404, response.getStatus());
    }

    @Test
    @DisplayName("isConstraintViolation with cause containing constraint keyword in cause message returns true")
    void isConstraintViolation_causeMessageHasConstraintKeyword_returnsTrue() {
        RuntimeException cause = new RuntimeException("duplicate key value violates unique constraint uq_test");
        RuntimeException wrapper = new RuntimeException((String) null, cause);
        assertTrue(mapper.isConstraintViolation(wrapper));
    }

    @Test
    @DisplayName("isConstraintViolation with cause with non-constraint message returns false")
    void isConstraintViolation_causeNonConstraintMessage_returnsFalse() {
        RuntimeException cause = new RuntimeException("something random happened");
        RuntimeException wrapper = new RuntimeException((String) null, cause);
        assertFalse(mapper.isConstraintViolation(wrapper));
    }

    static class FakeJsonMappingException extends RuntimeException {
        FakeJsonMappingException(String msg) {
            super(msg);
        }
    }
}
