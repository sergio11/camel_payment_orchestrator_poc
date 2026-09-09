package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorDetailDTO;
import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    private static final List<String> BAD_REQUEST_MARKERS = List.of(
        "JsonParseException", "JsonMappingException", "MismatchedInputException",
        "InvalidFormatException", "UnrecognizedPropertyException"
    );

    private static final List<String> CONSTRAINT_MARKERS = List.of(
        "duplicate", "unique", "constraint", "uq_", "uq "
    );

    private final PaymentNotFoundExceptionMapper notFoundMapper = new PaymentNotFoundExceptionMapper();
    private final ConstraintViolationExceptionMapper validationMapper = new ConstraintViolationExceptionMapper();
    private final PersistenceConflictExceptionMapper conflictMapper = new PersistenceConflictExceptionMapper();
    private final IllegalArgumentExceptionMapper illegalArgumentMapper = new IllegalArgumentExceptionMapper();

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof PaymentNotFoundException pnfe) {
            return notFoundMapper.toResponse(pnfe);
        }
        if (exception instanceof ConstraintViolationException cve) {
            return validationMapper.toResponse(cve);
        }
        if (exception instanceof IllegalArgumentException iae) {
            return illegalArgumentMapper.toResponse(iae);
        }
        if (exception instanceof EntityExistsException eee) {
            return conflictMapper.toResponse(eee);
        }
        if (exception instanceof PersistenceException pe) {
            if (isConstraintViolation(pe)) {
                return conflictMapper.toResponse(pe);
            }
        }
        if (exception instanceof SQLIntegrityConstraintViolationException sql) {
            return buildConflictResponse();
        }
        if (exception instanceof org.hibernate.exception.ConstraintViolationException hcve) {
            return buildConflictResponse();
        }
        if (exception instanceof BadRequestException bae) {
            return buildBadRequestResponse("Bad request", null);
        }
        if (exception instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        if (isBadRequest(exception)) {
            String msg = exception.getMessage();
            List<ErrorDetailDTO> details = (msg != null && !msg.isBlank())
                ? List.of(new ErrorDetailDTO("body", msg))
                : List.of(new ErrorDetailDTO("body", "Malformed request body"));
            return buildBadRequestResponse("Invalid request body", details);
        }
        if (isConstraintViolation(exception)) {
            return buildConflictResponse();
        }

        String errorId = UUID.randomUUID().toString().substring(0, 8);
        LOG.errorf(exception, "[%s] Unhandled exception: %s", errorId, exception.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponseDTO.from("INTERNAL_ERROR", "An unexpected error occurred [errorId=" + errorId + "]"))
            .build();
    }

    public boolean isBadRequest(Exception ex) {
        if (ex == null) return false;
        String name = ex.getClass().getName();
        if (matchesAny(name, BAD_REQUEST_MARKERS)) return true;
        Throwable cause = ex.getCause();
        if (cause != null && matchesAny(cause.getClass().getName(), BAD_REQUEST_MARKERS)) return true;
        return false;
    }

    public boolean isConstraintViolation(Exception ex) {
        if (ex == null) return false;
        String msg = ex.getMessage();
        if (msg != null && containsConstraintKeyword(msg)) return true;
        Throwable cause = ex.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && containsConstraintKeyword(causeMsg)) return true;
        }
        return false;
    }

    private boolean containsConstraintKeyword(String msg) {
        String lower = msg.toLowerCase();
        return isDuplicateKey(lower) || lower.contains("uq_") || lower.contains("uq ");
    }

    public static boolean matchesAny(String text, List<String> markers) {
        if (text == null || markers == null || markers.isEmpty()) return false;
        for (String marker : markers) {
            if (text.contains(marker)) return true;
        }
        return false;
    }

    public static boolean isDuplicateKey(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("duplicate") && lower.contains("key");
    }

    private Response buildBadRequestResponse(String message, List<ErrorDetailDTO> details) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(details != null
                ? ErrorResponseDTO.from("VALIDATION_ERROR", message, details)
                : ErrorResponseDTO.from("VALIDATION_ERROR", message))
            .build();
    }

    private Response buildConflictResponse() {
        return Response.status(Response.Status.CONFLICT)
            .entity(ErrorResponseDTO.from("CONFLICT", "Duplicate request or constraint violation"))
            .build();
    }
}