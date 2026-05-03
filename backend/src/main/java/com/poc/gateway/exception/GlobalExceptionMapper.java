package com.poc.gateway.exception;

import com.poc.gateway.dto.ErrorResponse;
import com.poc.gateway.dto.ErrorResponse.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.stream.Collectors;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ConstraintViolationException) {
            return handleValidationException((ConstraintViolationException) exception);
        }
        if (exception instanceof PaymentNotFoundException) {
            return handleNotFound((PaymentNotFoundException) exception);
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.from("INTERNAL_ERROR", "An unexpected error occurred"))
            .build();
    }

    private Response handleValidationException(ConstraintViolationException e) {
        List<ErrorDetail> details = e.getConstraintViolations().stream()
            .map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .collect(Collectors.toList());
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.from("VALIDATION_ERROR", "Invalid request", details))
            .build();
    }

    private Response handleNotFound(PaymentNotFoundException e) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse.from("NOT_FOUND", e.getMessage()))
            .build();
    }
}