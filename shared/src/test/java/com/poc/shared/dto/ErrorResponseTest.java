package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    @Test
    @DisplayName("from(error, message) should return correct error, message, null details, and non-null timestamp")
    void testFromWithTwoArgs() {
        ErrorResponseDTO response = ErrorResponseDTO.from("NOT_FOUND", "Resource not found");

        assertEquals("NOT_FOUND", response.error());
        assertEquals("Resource not found", response.message());
        assertNull(response.details());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("from(error, message, details) should return correct details list")
    void testFromWithThreeArgs() {
        ErrorDetailDTO detail1 = new ErrorDetailDTO("field1", "message1");
        ErrorDetailDTO detail2 = new ErrorDetailDTO("field2", "message2");
        List<ErrorDetailDTO> details = List.of(detail1, detail2);

        ErrorResponseDTO response = ErrorResponseDTO.from("VALIDATION_ERROR", "Validation failed", details);

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
    @DisplayName("ErrorDetailDTO record should expose field and message")
    void testErrorDetailRecord() {
        ErrorDetailDTO detail = new ErrorDetailDTO("amount", "must be positive");

        assertEquals("amount", detail.field());
        assertEquals("must be positive", detail.message());
    }

    @Test
    @DisplayName("Record construction with all parameters should work")
    void testRecordConstruction() {
        ErrorDetailDTO detail = new ErrorDetailDTO("id", "invalid");
        ErrorResponseDTO response = new ErrorResponseDTO("ERROR", "msg", List.of(detail), null);

        assertEquals("ERROR", response.error());
        assertEquals("msg", response.message());
        assertNotNull(response.details());
        assertNull(response.timestamp());
    }
}
