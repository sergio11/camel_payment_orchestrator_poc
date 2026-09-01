package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    @Test
    @DisplayName("from(error, message) should return correct error, message, null details, and non-null timestamp")
    void testFromWithTwoArgs() {
        ErrorResponse response = ErrorResponse.from("NOT_FOUND", "Resource not found");

        assertEquals("NOT_FOUND", response.error());
        assertEquals("Resource not found", response.message());
        assertNull(response.details());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("from(error, message, details) should return correct details list")
    void testFromWithThreeArgs() {
        ErrorResponse.ErrorDetail detail1 = new ErrorResponse.ErrorDetail("field1", "message1");
        ErrorResponse.ErrorDetail detail2 = new ErrorResponse.ErrorDetail("field2", "message2");
        List<ErrorResponse.ErrorDetail> details = List.of(detail1, detail2);

        ErrorResponse response = ErrorResponse.from("VALIDATION_ERROR", "Validation failed", details);

        assertEquals("VALIDATION_ERROR", response.error());
        assertEquals("Validation failed", response.message());
        assertNotNull(response.details());
        assertEquals(2, response.details().size());
        assertEquals("field1", response.details().get(0).field());
        assertEquals("message1", response.details().get(0).message());
        assertEquals("field2", response.details().get(1).field());
        assertEquals("message2", response.details().get(1).message());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("ErrorDetail record should expose field and message")
    void testErrorDetailRecord() {
        ErrorResponse.ErrorDetail detail = new ErrorResponse.ErrorDetail("amount", "must be positive");

        assertEquals("amount", detail.field());
        assertEquals("must be positive", detail.message());
    }

    @Test
    @DisplayName("Record construction with all parameters should work")
    void testRecordConstruction() {
        ErrorResponse.ErrorDetail detail = new ErrorResponse.ErrorDetail("id", "invalid");
        ErrorResponse response = new ErrorResponse("ERROR", "msg", List.of(detail), null);

        assertEquals("ERROR", response.error());
        assertEquals("msg", response.message());
        assertNotNull(response.details());
        assertNull(response.timestamp());
    }
}
