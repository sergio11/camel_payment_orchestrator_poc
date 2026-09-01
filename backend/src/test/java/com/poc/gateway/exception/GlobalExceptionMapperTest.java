package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponse;
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
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals("NOT_FOUND", error.error());
        assertTrue(error.message().contains("123"));
    }

    @Test
    @DisplayName("Generic exception returns 500")
    void testGenericExceptionReturns500() {
        Response response = mapper.toResponse(new RuntimeException("Something went wrong"));
        assertEquals(500, response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
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
        ErrorResponse error = (ErrorResponse) response.getEntity();
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
        ErrorResponse error = (ErrorResponse) response.getEntity();
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
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals("VALIDATION_ERROR", error.error());
        assertEquals(1, error.details().size());
        assertEquals("name", error.details().get(0).field());
        assertEquals("must not be null", error.details().get(0).message());
    }

    @Test
    @DisplayName("PaymentNotFoundException has correct error code and message")
    void testPaymentNotFoundExceptionErrorDetails() {
        Response response = mapper.toResponse(new PaymentNotFoundException("pay-xyz"));
        ErrorResponse error = (ErrorResponse) response.getEntity();

        assertEquals("NOT_FOUND", error.error());
        assertEquals("Payment not found: pay-xyz", error.message());
        assertNull(error.details());
    }
}
