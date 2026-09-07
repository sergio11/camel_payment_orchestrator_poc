package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponse;
import com.poc.shared.dto.ErrorResponse.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ConstraintViolationException cve) {
            return handleValidationException(cve);
        }
        if (exception instanceof IllegalArgumentException iae) {
            return handleIllegalArgument(iae);
        }
        if (exception instanceof PaymentNotFoundException pnfe) {
            return handleNotFound(pnfe);
        }
        if (isBadRequest(exception)) {
            return handleBadRequest(exception);
        }
        if (isConstraintViolation(exception)) {
            return handleConflict(exception);
        }
        return handleGeneric(exception);
    }

    private Response handleBadRequest(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Malformed request body";
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.from("VALIDATION_ERROR", "Invalid request",
                List.of(new ErrorResponse.ErrorDetail("body", msg))))
            .build();
    }

    private static final List<String> BAD_REQUEST_MARKERS = List.of(
        "JsonProcessingException", "JsonMappingException", "JsonParseException",
        "MismatchedInputException", "InvalidFormatException", "UnrecognizedPropertyException",
        "NotNullConstraintViolationException", "ResteasyReactive",
        "MessageBodyProviderNotFoundException", "BadRequestException", "ClientErrorException"
    );

    boolean isBadRequest(Throwable t) {
        while (t != null) {
            String cls = t.getClass().getName();
            if (t instanceof jakarta.ws.rs.BadRequestException || matchesAny(cls, BAD_REQUEST_MARKERS)) {
                return true;
            }
            if (t instanceof NullPointerException && cls.contains("gateway")) {
                return false;
            }
            t = t.getCause();
        }
        return false;
    }

    static boolean matchesAny(String value, List<String> markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private Response handleIllegalArgument(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.from("INVALID_ARGUMENT", e.getMessage()))
            .build();
    }

    private Response handleValidationException(ConstraintViolationException e) {
        List<ErrorDetail> details = e.getConstraintViolations().stream()
            .map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.from("VALIDATION_ERROR", "Invalid request", details))
            .build();
    }

    private Response handleNotFound(PaymentNotFoundException e) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse.from("NOT_FOUND", e.getMessage()))
            .build();
    }

    private Response handleConflict(Exception e) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        LOG.errorf(e, "[%s] Duplicate constraint violation: %s", errorId, e.getMessage());
        return Response.status(Response.Status.CONFLICT)
            .entity(ErrorResponse.from("CONFLICT", "Duplicate request [errorId=" + errorId + "]",
                List.of(new ErrorDetail("idempotencyKey", "Duplicate key or constraint violation"))))
            .build();
    }

    private Response handleGeneric(Exception e) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        LOG.errorf(e, "[%s] Unhandled exception: %s", errorId, e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.from("INTERNAL_ERROR", "An unexpected error occurred [errorId=" + errorId + "]"))
            .build();
    }

    private static final List<String> CONSTRAINT_CLASS_MARKERS = List.of(
        "ConstraintViolationException", "EntityExistsException"
    );
    private static final List<String> CONSTRAINT_MESSAGE_MARKERS = List.of(
        "constraint", "unique", "duplicate"
    );
    private static final List<String> IDEMPOTENCY_MESSAGE_MARKERS = List.of(
        "uq_payments_idempotency", "uq_outbox_idempotency", "unique constraint", "unique index"
    );

    boolean isConstraintViolation(Throwable t) {
        while (t != null) {
            String cls = t.getClass().getName();
            if (t instanceof jakarta.persistence.PersistenceException
                || t instanceof java.sql.SQLIntegrityConstraintViolationException
                || matchesAny(cls, CONSTRAINT_CLASS_MARKERS)) {
                String msg = String.valueOf(t.getMessage()).toLowerCase();
                if (matchesAny(msg, CONSTRAINT_MESSAGE_MARKERS)
                    || cls.contains("ConstraintViolation")
                    || t instanceof jakarta.persistence.EntityExistsException) {
                    return true;
                }
            }
            String msg = String.valueOf(t.getMessage()).toLowerCase();
            if (matchesAny(msg, IDEMPOTENCY_MESSAGE_MARKERS) || isDuplicateKey(msg)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    static boolean isDuplicateKey(String msg) {
        return msg.contains("duplicate") && msg.contains("key");
    }
}