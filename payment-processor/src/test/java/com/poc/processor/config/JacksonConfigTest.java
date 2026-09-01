package com.poc.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JacksonConfigTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    @DisplayName("ObjectMapper handles JavaTimeModule via serialization of LocalDateTime")
    void hasJavaTimeModule() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
        String json = objectMapper.writeValueAsString(now);
        assertNotNull(json);
        assertTrue(json.contains("2026"), "Serialized LocalDateTime should contain the year");
        assertFalse(json.matches("\\d+"), "LocalDateTime should not be serialized as a plain numeric timestamp");
    }

    @Test
    @DisplayName("ObjectMapper does not write dates as timestamps")
    void doesNotWriteDatesAsTimestamps() {
        assertFalse(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }
}
