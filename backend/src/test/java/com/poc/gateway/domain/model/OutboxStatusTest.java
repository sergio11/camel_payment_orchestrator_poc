package com.poc.gateway.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutboxStatusTest {

    @Test
    void allValues() {
        assertEquals(2, OutboxStatus.values().length);
    }

    @Test
    void valueOf_validNames() {
        assertEquals(OutboxStatus.PENDING, OutboxStatus.valueOf("PENDING"));
        assertEquals(OutboxStatus.SENT, OutboxStatus.valueOf("SENT"));
    }

    @Test
    void valueOf_invalidName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> OutboxStatus.valueOf("UNKNOWN"));
    }
}
