package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorDetailDTO;
import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Inject
    ExceptionClassifier classifier;

    @Override
    public Response toResponse(Throwable exception) {
        LOG.debugf("Handling exception: %s", exception.getClass().getSimpleName());

        if (exception instanceof ClientErrorException clientError) {
            return clientError.getResponse();
        }

        ExceptionCategory category = classifier.classify(exception);

        return switch (category) {
            case NOT_FOUND -> buildResponse(Response.Status.NOT_FOUND, "NOT_FOUND", exception);
            case BAD_REQUEST -> buildResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST", exception);
            case CONFLICT -> buildResponse(Response.Status.CONFLICT, "CONFLICT", exception);
            case UNAUTHORIZED -> buildResponse(Response.Status.UNAUTHORIZED, "UNAUTHORIZED", exception);
            case FORBIDDEN -> buildResponse(Response.Status.FORBIDDEN, "FORBIDDEN", exception);
            default -> buildResponse(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", exception);
        };
    }

    private Response buildResponse(Response.Status status, String error, Throwable exception) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.from(
            error,
            exception.getMessage(),
            List.of(new ErrorDetailDTO(error, exception.getMessage()))
        );
        return Response.status(status).entity(errorResponse).build();
    }
}