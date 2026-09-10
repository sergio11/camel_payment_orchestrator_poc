package com.poc.gateway.domain.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionTest {

    @Test
    void constructor_withMessage() {
        DomainException ex = new DomainException("error");
        assertEquals("error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void constructor_withCause() {
        RuntimeException cause = new RuntimeException("root");
        DomainException ex = new DomainException("error", cause);
        assertEquals("error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        DomainException ex = new DomainException("error");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void constructor_withNullMessage() {
        DomainException ex = new DomainException(null);
        assertNull(ex.getMessage());
    }

    @Test
    void constructor_withNullCause() {
        DomainException ex = new DomainException("error", null);
        assertEquals("error", ex.getMessage());
        assertNull(ex.getCause());
    }
}
