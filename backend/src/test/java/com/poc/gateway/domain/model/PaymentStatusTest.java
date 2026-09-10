package com.poc.gateway.domain.model;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void fromString_validStatus() {
        assertEquals(Optional.of(PaymentStatus.PENDING), PaymentStatus.fromString("PENDING"));
        assertEquals(Optional.of(PaymentStatus.APPROVED), PaymentStatus.fromString("APPROVED"));
        assertEquals(Optional.of(PaymentStatus.REJECTED), PaymentStatus.fromString("REJECTED"));
        assertEquals(Optional.of(PaymentStatus.FAILED), PaymentStatus.fromString("FAILED"));
        assertEquals(Optional.of(PaymentStatus.REVIEW), PaymentStatus.fromString("REVIEW"));
        assertEquals(Optional.of(PaymentStatus.PROCESSING), PaymentStatus.fromString("PROCESSING"));
    }

    @Test
    void fromString_null_returnsEmpty() {
        assertTrue(PaymentStatus.fromString(null).isEmpty());
    }

    @Test
    void fromString_blank_returnsEmpty() {
        assertTrue(PaymentStatus.fromString("").isEmpty());
        assertTrue(PaymentStatus.fromString("  ").isEmpty());
        assertTrue(PaymentStatus.fromString("\t").isEmpty());
    }

    @Test
    void fromString_invalid_returnsEmpty() {
        assertTrue(PaymentStatus.fromString("TYPO").isEmpty());
        assertTrue(PaymentStatus.fromString("pending").isEmpty());
        assertTrue(PaymentStatus.fromString("PENDING ").isEmpty());
    }

    @Test
    void allValues() {
        assertEquals(6, PaymentStatus.values().length);
    }

    @Test
    void valueOf_validName() {
        assertEquals(PaymentStatus.PENDING, PaymentStatus.valueOf("PENDING"));
        assertEquals(PaymentStatus.PROCESSING, PaymentStatus.valueOf("PROCESSING"));
        assertEquals(PaymentStatus.APPROVED, PaymentStatus.valueOf("APPROVED"));
        assertEquals(PaymentStatus.REJECTED, PaymentStatus.valueOf("REJECTED"));
        assertEquals(PaymentStatus.FAILED, PaymentStatus.valueOf("FAILED"));
        assertEquals(PaymentStatus.REVIEW, PaymentStatus.valueOf("REVIEW"));
    }

    @Test
    void valueOf_invalidName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> PaymentStatus.valueOf("TYPO"));
    }
}
