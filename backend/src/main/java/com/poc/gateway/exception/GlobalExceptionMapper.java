package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponse;
import com.poc.shared.dto.ErrorResponse.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {
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

    private Response handleGeneric(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.from("INTERNAL_ERROR", "An unexpected error occurred"))
            .build();
    }
}