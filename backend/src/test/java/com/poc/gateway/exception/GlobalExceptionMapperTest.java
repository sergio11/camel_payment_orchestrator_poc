package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionMapperTest {

    private GlobalExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GlobalExceptionMapper();
    }

    @Test
    @DisplayName("PaymentNotFoundException returns 404")
    void testPaymentNotFoundExceptionReturns404() {
        Response response = mapper.toResponse(new PaymentNotFoundException("123"));
        assertEquals(404, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("NOT_FOUND", error.error());
        assertTrue(error.message().contains("123"));
    }

    @Test
    @DisplayName("Generic exception returns 500")
    void testGenericExceptionReturns500() {
        Response response = mapper.toResponse(new RuntimeException("Something went wrong"));
        assertEquals(500, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("INTERNAL_ERROR", error.error());
    }

    @Test
    @DisplayName("ConstraintViolationException returns 400 with validation details")
    void testConstraintViolationExceptionReturns400() {
        jakarta.validation.Path path1 = mock(jakarta.validation.Path.class);
        when(path1.toString()).thenReturn("amount");

        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        when(violation1.getPropertyPath()).thenReturn(path1);
        when(violation1.getMessage()).thenReturn("must be positive");

        jakarta.validation.Path path2 = mock(jakarta.validation.Path.class);
        when(path2.toString()).thenReturn("currency");

        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        when(violation2.getPropertyPath()).thenReturn(path2);
        when(violation2.getMessage()).thenReturn("must not be blank");

        ConstraintViolationException cve = new ConstraintViolationException(
            "Validation failed", Set.of(violation1, violation2)
        );

        Response response = mapper.toResponse(cve);

        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
        assertEquals("Invalid request", error.message());
        assertNotNull(error.details());
        assertEquals(2, error.details().size());
    }

    @Test
    @DisplayName("IllegalArgumentException returns 400")
    void testIllegalArgumentExceptionReturns400() {
        Response response = mapper.toResponse(new IllegalArgumentException("bad argument"));

        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("INVALID_ARGUMENT", error.error());
        assertEquals("bad argument", error.message());
    }

    @Test
    @DisplayName("ConstraintViolationException with single violation returns 400")
    void testConstraintViolationExceptionSingleViolationReturns400() {
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("name");

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be null");

        ConstraintViolationException cve = new ConstraintViolationException(
            "Validation failed", Set.of(violation)
        );

        Response response = mapper.toResponse(cve);

        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
        assertEquals(1, error.details().size());
        assertEquals("name", error.details().get(0).field());
        assertEquals("must not be null", error.details().get(0).message());
    }

    @Test
    @DisplayName("PaymentNotFoundException has correct error code and message")
    void testPaymentNotFoundExceptionErrorDetails() {
        Response response = mapper.toResponse(new PaymentNotFoundException("pay-xyz"));
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();

        assertEquals("NOT_FOUND", error.error());
        assertEquals("Payment not found: pay-xyz", error.message());
        assertNull(error.details());
    }

    @Test
    @DisplayName("Jackson JsonParseException returns 400")
    void testJacksonParseExceptionReturns400() {
        com.fasterxml.jackson.core.JsonParseException ex =
            new com.fasterxml.jackson.core.JsonParseException(null, "Unexpected character");
        Response response = mapper.toResponse(ex);
        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
    }

    @Test
    @DisplayName("Wrapped Jackson exception returns 400")
    void testWrappedJacksonExceptionReturns400() {
        com.fasterxml.jackson.core.JsonParseException cause =
            new com.fasterxml.jackson.core.JsonParseException(null, "bad json");
        RuntimeException wrapper = new RuntimeException("deserialization failed", cause);
        Response response = mapper.toResponse(wrapper);
        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
    }

    @Test
    @DisplayName("BadRequestException returns 400")
    void testBadRequestExceptionReturns400() {
        Response response = mapper.toResponse(new jakarta.ws.rs.BadRequestException("bad body"));
        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
    }

    static class FakeJsonParseException extends RuntimeException {
        FakeJsonParseException() {
            super((String) null);
        }
    }

    @Test
    @DisplayName("Jackson exception without message uses fallback text")
    void testJacksonExceptionNullMessageUsesFallback() {
        Response response = mapper.toResponse(new FakeJsonParseException());
        assertEquals(400, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
        assertEquals(1, error.details().size());
        assertEquals("Malformed request body", error.details().get(0).message());
    }

    @Test
    @DisplayName("NullPointerException still returns 500")
    void testNullPointerExceptionReturns500() {
        Response response = mapper.toResponse(new NullPointerException("oops"));
        assertEquals(500, response.getStatus());
    }

    @Test
    @DisplayName("DB unique constraint violation returns 409")
    void testUniqueViolationReturns409() {
        jakarta.persistence.PersistenceException ex =
            new jakarta.persistence.PersistenceException("duplicate key value violates unique constraint \"uq_payments_idempotency\"");
        Response response = mapper.toResponse(ex);
        assertEquals(409, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("CONFLICT", error.error());
    }

    @Test
    @DisplayName("isBadRequest detects wrapped Jackson causes")
    void testIsBadRequestWrappedCause() {
        com.fasterxml.jackson.core.JsonParseException cause =
            new com.fasterxml.jackson.core.JsonParseException(null, "bad");
        assertTrue(mapper.isBadRequest(new RuntimeException("wrap", cause)));
        assertFalse(mapper.isBadRequest(new RuntimeException("plain")));
    }

    static class GatewayNpe extends NullPointerException {
        GatewayNpe(String msg) {
            super(msg);
        }
    }

    @Test
    @DisplayName("Gateway NullPointerException is not treated as bad request")
    void testGatewayNpeNotBadRequest() {
        Response response = mapper.toResponse(new GatewayNpe("npe in gateway"));
        assertEquals(500, response.getStatus());
    }

    @Test
    @DisplayName("Non-persistence exception with idempotency message returns 409")
    void testIdempotencyMessageReturns409() {
        Response response = mapper.toResponse(new RuntimeException("violates uq_outbox_idempotency"));
        assertEquals(409, response.getStatus());
        ErrorResponseDTO error = (ErrorResponseDTO) response.getEntity();
        assertEquals("CONFLICT", error.error());
    }

    @Test
    @DisplayName("Wrapped constraint message returns 409")
    void testWrappedConstraintMessageReturns409() {
        RuntimeException cause = new RuntimeException("duplicate key issue");
        Response response = mapper.toResponse(new RuntimeException("wrap", cause));
        assertEquals(409, response.getStatus());
    }

    @Test
    @DisplayName("Unmatched causal chain returns 500")
    void testUnmatchedChainReturns500() {
        Response response = mapper.toResponse(new RuntimeException("a", new RuntimeException("b")));
        assertEquals(500, response.getStatus());
    }

    @Test
    @DisplayName("SQL integrity violation with unique message returns 409")
    void testSqlIntegrityViolationReturns409() {
        java.sql.SQLIntegrityConstraintViolationException ex =
            new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key 'uq'");
        Response response = mapper.toResponse(ex);
        assertEquals(409, response.getStatus());
    }

    @Test
    @DisplayName("EntityExistsException returns 409")
    void testEntityExistsReturns409() {
        jakarta.persistence.EntityExistsException ex =
            new jakarta.persistence.EntityExistsException("already exists");
        Response response = mapper.toResponse(ex);
        assertEquals(409, response.getStatus());
    }

    @Test
    @DisplayName("PersistenceException without keyword message returns 500")
    void testPersistenceNoKeywordReturns500() {
        jakarta.persistence.PersistenceException ex =
            new jakarta.persistence.PersistenceException("something else");
        Response response = mapper.toResponse(ex);
        assertEquals(500, response.getStatus());
    }

    @Test
    @DisplayName("Hibernate constraint violation with constraint message returns 409")
    void testHibernateViolationReturns409() {
        org.hibernate.exception.ConstraintViolationException ex =
            new org.hibernate.exception.ConstraintViolationException(
                "could not execute statement", new java.sql.SQLException("integrity"), "tbl");
        Response response = mapper.toResponse(ex);
        assertEquals(409, response.getStatus());
    }

    @Test
    @DisplayName("Duplicate without key is not a conflict")
    void testDuplicateWithoutKeyReturns500() {
        Response response = mapper.toResponse(new RuntimeException("duplicate entry omitted"));
        assertEquals(500, response.getStatus());
    }

    @Test
    @DisplayName("matchesAny detects markers")
    void testMatchesAny() {
        assertTrue(GlobalExceptionMapper.matchesAny("aJsonParseExceptionb", java.util.List.of("JsonParseException")));
        assertFalse(GlobalExceptionMapper.matchesAny("plain", java.util.List.of("JsonParseException")));
        assertFalse(GlobalExceptionMapper.matchesAny("plain", java.util.List.of()));
    }

    @Test
    @DisplayName("isDuplicateKey requires both words")
    void testIsDuplicateKey() {
        assertTrue(GlobalExceptionMapper.isDuplicateKey("duplicate key issue"));
        assertFalse(GlobalExceptionMapper.isDuplicateKey("duplicate entry omitted"));
        assertFalse(GlobalExceptionMapper.isDuplicateKey("some key problem"));
    }

    @Test
    @DisplayName("matchers handle null input without throwing")
    void testMatchersNullInput() {
        assertFalse(mapper.isBadRequest(null));
        assertFalse(mapper.isConstraintViolation(null));
    }

    static class FakeEntityExistsException extends RuntimeException {
        FakeEntityExistsException(String msg) {
            super(msg);
        }
    }

    @Test
    @DisplayName("EntityExists-like class name without persistence type is not a conflict")
    void testFakeEntityExistsReturns500() {
        Response response = mapper.toResponse(new FakeEntityExistsException("plain problem"));
        assertEquals(500, response.getStatus());
    }
}
