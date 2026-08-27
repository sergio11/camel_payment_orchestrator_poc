package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionMapperTest {

    private GlobalExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GlobalExceptionMapper();
    }

    @Test
    void testPaymentNotFoundExceptionReturns404() {
        Response response = mapper.toResponse(new PaymentNotFoundException("123"));
        assertEquals(404, response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals("NOT_FOUND", error.error());
    }

    @Test
    void testGenericExceptionReturns500() {
        Response response = mapper.toResponse(new RuntimeException("Something went wrong"));
        assertEquals(500, response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals("INTERNAL_ERROR", error.error());
    }
}
