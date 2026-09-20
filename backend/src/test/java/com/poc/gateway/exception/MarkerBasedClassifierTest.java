package com.poc.gateway.exception;

import com.poc.gateway.domain.exception.PaymentNotFoundException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkerBasedClassifierTest {

    private final MarkerBasedClassifier classifier = new MarkerBasedClassifier();

    @Test
    void classify_paymentNotFound_returnsNotFound() {
        assertEquals(ExceptionCategory.NOT_FOUND, classifier.classify(new PaymentNotFoundException("x")));
    }

    @Test
    void classify_illegalArgument_returnsBadRequest() {
        assertEquals(ExceptionCategory.BAD_REQUEST, classifier.classify(new IllegalArgumentException("bad")));
    }

    @Test
    void classify_numberFormat_returnsBadRequest() {
        assertEquals(ExceptionCategory.BAD_REQUEST, classifier.classify(new NumberFormatException("bad")));
    }

    @Test
    void classify_constraintInMessage_returnsConflict() {
        RuntimeException ex = new RuntimeException("unique constraint violation");
        assertEquals(ExceptionCategory.CONFLICT, classifier.classify(ex));
    }

    @Test
    void classify_duplicateInMessage_returnsConflict() {
        RuntimeException ex = new RuntimeException("duplicate key value");
        assertEquals(ExceptionCategory.CONFLICT, classifier.classify(ex));
    }

    @Test
    void classify_uqPrefixInMessage_returnsConflict() {
        RuntimeException ex = new RuntimeException("uq_payments_idempotency");
        assertEquals(ExceptionCategory.CONFLICT, classifier.classify(ex));
    }

    @Test
    void classify_unknown_returnsInternal() {
        assertEquals(ExceptionCategory.INTERNAL, classifier.classify(new RuntimeException("unknown")));
    }

    @Test
    void classify_nullMessage_returnsInternal() {
        assertEquals(ExceptionCategory.INTERNAL, classifier.classify(new RuntimeException()));
    }

    @Test
    void classify_classNameContainingDuplicate_returnsConflict() {
        DuplicateKeyException ex = new DuplicateKeyException("some error without markers");
        assertEquals(ExceptionCategory.CONFLICT, classifier.classify(ex));
    }

    @Test
    void classify_emptyMessage_returnsInternal() {
        assertEquals(ExceptionCategory.INTERNAL, classifier.classify(new RuntimeException("")));
    }

    @Test
    void classify_messageContains400_returnsBadRequest() {
        assertEquals(ExceptionCategory.BAD_REQUEST, classifier.classify(new RuntimeException("HTTP 400 error")));
    }

    @Test
    void classify_messageContainsBadRequest_returnsBadRequest() {
        assertEquals(ExceptionCategory.BAD_REQUEST, classifier.classify(new RuntimeException("bad request")));
    }

    private static class DuplicateKeyException extends RuntimeException {
        DuplicateKeyException(String message) {
            super(message);
        }
    }
}
