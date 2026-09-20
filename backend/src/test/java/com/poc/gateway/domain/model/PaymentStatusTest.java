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

    // ========== canTransitionTo ==========

    @Test
    void canTransitionTo_pendingAllowsProcessing() {
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PROCESSING));
    }

    @Test
    void canTransitionTo_pendingAllowsFailed() {
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void canTransitionTo_pendingAllowsRejected() {
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REJECTED));
    }

    @Test
    void canTransitionTo_pendingRejectsApproved() {
        assertFalse(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.APPROVED));
    }

    @Test
    void canTransitionTo_pendingRejectsReview() {
        assertFalse(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REVIEW));
    }

    @Test
    void canTransitionTo_processingAllowsApproved() {
        assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.APPROVED));
    }

    @Test
    void canTransitionTo_processingAllowsRejected() {
        assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.REJECTED));
    }

    @Test
    void canTransitionTo_processingAllowsFailed() {
        assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void canTransitionTo_processingAllowsReview() {
        assertTrue(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.REVIEW));
    }

    @Test
    void canTransitionTo_processingRejectsPending() {
        assertFalse(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PENDING));
    }

    @Test
    void canTransitionTo_reviewAllowsProcessing() {
        assertTrue(PaymentStatus.REVIEW.canTransitionTo(PaymentStatus.PROCESSING));
    }

    @Test
    void canTransitionTo_reviewAllowsApproved() {
        assertTrue(PaymentStatus.REVIEW.canTransitionTo(PaymentStatus.APPROVED));
    }

    @Test
    void canTransitionTo_reviewAllowsRejected() {
        assertTrue(PaymentStatus.REVIEW.canTransitionTo(PaymentStatus.REJECTED));
    }

    @Test
    void canTransitionTo_reviewAllowsFailed() {
        assertTrue(PaymentStatus.REVIEW.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void canTransitionTo_reviewRejectsPending() {
        assertFalse(PaymentStatus.REVIEW.canTransitionTo(PaymentStatus.PENDING));
    }

    @Test
    void canTransitionTo_failedAllowsPending() {
        assertTrue(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING));
    }

    @Test
    void canTransitionTo_failedRejectsAllOthers() {
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PROCESSING));
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.APPROVED));
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REJECTED));
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REVIEW));
    }

    @Test
    void canTransitionTo_approvedIsTerminal() {
        assertFalse(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.PENDING));
        assertFalse(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.PROCESSING));
        assertFalse(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.REJECTED));
        assertFalse(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.FAILED));
        assertFalse(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.REVIEW));
    }

    @Test
    void canTransitionTo_rejectedIsTerminal() {
        assertFalse(PaymentStatus.REJECTED.canTransitionTo(PaymentStatus.PENDING));
        assertFalse(PaymentStatus.REJECTED.canTransitionTo(PaymentStatus.PROCESSING));
        assertFalse(PaymentStatus.REJECTED.canTransitionTo(PaymentStatus.APPROVED));
        assertFalse(PaymentStatus.REJECTED.canTransitionTo(PaymentStatus.FAILED));
        assertFalse(PaymentStatus.REJECTED.canTransitionTo(PaymentStatus.REVIEW));
    }
}
