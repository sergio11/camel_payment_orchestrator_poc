package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IllegalArgumentExceptionMapperTest {

    private IllegalArgumentExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new IllegalArgumentExceptionMapper();
    }

    @Test
    @DisplayName("toResponse returns 400 status")
    void toResponse_returns400Status() {
        Response response = mapper.toResponse(new IllegalArgumentException("bad input"));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("toResponse body has INVALID_ARGUMENT error code")
    void toResponse_bodyHasInvalidArgumentErrorCode() {
        Response response = mapper.toResponse(new IllegalArgumentException("bad input"));

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertNotNull(body);
        assertEquals("INVALID_ARGUMENT", body.error());
    }

    @Test
    @DisplayName("toResponse body message matches exception message")
    void toResponse_bodyMessageMatchesExceptionMessage() {
        Response response = mapper.toResponse(new IllegalArgumentException("specific error message"));

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertNotNull(body);
        assertEquals("specific error message", body.message());
    }

    @Test
    @DisplayName("toResponse body has null timestamp")
    void toResponse_bodyHasTimestamp() {
        Response response = mapper.toResponse(new IllegalArgumentException("err"));

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertNotNull(body);
        assertNotNull(body.timestamp());
    }
}
