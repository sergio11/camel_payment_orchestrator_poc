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
        if (isConstraintViolation(exception)) {
            return handleConflict(exception);
        }
        return handleGeneric(exception);
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

    boolean isConstraintViolation(Throwable t) {
        while (t != null) {
            String cls = t.getClass().getName();
            if (t instanceof jakarta.persistence.PersistenceException
                || t instanceof java.sql.SQLIntegrityConstraintViolationException
                || cls.contains("ConstraintViolationException")
                || cls.contains("EntityExistsException")) {
                String msg = String.valueOf(t.getMessage()).toLowerCase();
                if (msg.contains("constraint") || msg.contains("unique") || msg.contains("duplicate")
                    || cls.contains("ConstraintViolation") || t instanceof jakarta.persistence.EntityExistsException) {
                    return true;
                }
            }
            String msg = String.valueOf(t.getMessage()).toLowerCase();
            if (msg.contains("uq_payments_idempotency") || msg.contains("uq_outbox_idempotency")
                || (msg.contains("duplicate") && msg.contains("key"))
                || msg.contains("unique constraint") || msg.contains("unique index")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}