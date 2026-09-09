package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorDetailDTO;
import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ErrorDetailDTO> details = exception.getConstraintViolations().stream()
            .map(v -> new ErrorDetailDTO(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponseDTO.from("VALIDATION_ERROR", "Invalid request", details))
            .build();
    }
}
