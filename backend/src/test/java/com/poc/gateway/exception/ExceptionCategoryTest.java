package com.poc.gateway.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionCategoryTest {

    @Test
    void allValues() {
        assertEquals(6, ExceptionCategory.values().length);
    }

    @Test
    void valueOf_validNames() {
        assertEquals(ExceptionCategory.NOT_FOUND, ExceptionCategory.valueOf("NOT_FOUND"));
        assertEquals(ExceptionCategory.BAD_REQUEST, ExceptionCategory.valueOf("BAD_REQUEST"));
        assertEquals(ExceptionCategory.CONFLICT, ExceptionCategory.valueOf("CONFLICT"));
        assertEquals(ExceptionCategory.UNAUTHORIZED, ExceptionCategory.valueOf("UNAUTHORIZED"));
        assertEquals(ExceptionCategory.FORBIDDEN, ExceptionCategory.valueOf("FORBIDDEN"));
        assertEquals(ExceptionCategory.INTERNAL, ExceptionCategory.valueOf("INTERNAL"));
    }

    @Test
    void valueOf_invalidName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> ExceptionCategory.valueOf("UNKNOWN"));
    }
}
