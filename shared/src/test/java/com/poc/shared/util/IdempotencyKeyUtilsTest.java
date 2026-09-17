package com.poc.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyKeyUtilsTest {

    @Test
    @DisplayName("normalize(null) should return a valid UUID string")
    void testNormalizeNull() {
        String result = IdempotencyKeyUtils.normalize(null);

        assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("normalize(blank) should return a valid UUID string")
    void testNormalizeBlank() {
        String result = IdempotencyKeyUtils.normalize("");

        assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("normalize(whitespace-only) should return a valid UUID string")
    void testNormalizeWhitespace() {
        String result = IdempotencyKeyUtils.normalize("   ");

        assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("normalize(valid key) should return trimmed key")
    void testNormalizeValidKey() {
        String result = IdempotencyKeyUtils.normalize("  my-key-123  ");

        assertEquals("my-key-123", result);
    }

    @Test
    @DisplayName("normalize(key without spaces) should return unchanged key")
    void testNormalizeUnchanged() {
        String result = IdempotencyKeyUtils.normalize("abc-456");

        assertEquals("abc-456", result);
    }
}
