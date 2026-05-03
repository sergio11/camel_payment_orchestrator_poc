package com.poc.gateway.exception;

import com.poc.gateway.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PaymentNotFoundExceptionMapper implements ExceptionMapper<PaymentNotFoundException> {
    @Override
    public Response toResponse(PaymentNotFoundException e) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse.from("NOT_FOUND", e.getMessage()))
            .build();
    }
}