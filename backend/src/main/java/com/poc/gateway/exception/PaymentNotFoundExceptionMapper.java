package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PaymentNotFoundExceptionMapper implements ExceptionMapper<PaymentNotFoundException> {

    @Override
    public Response toResponse(PaymentNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponseDTO.from("NOT_FOUND", exception.getMessage()))
            .build();
    }
}
