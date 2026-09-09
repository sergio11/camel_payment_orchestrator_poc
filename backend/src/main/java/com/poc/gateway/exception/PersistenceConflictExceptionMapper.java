package com.poc.gateway.exception;

import com.poc.shared.dto.ErrorDetailDTO;
import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

@Provider
public class PersistenceConflictExceptionMapper implements ExceptionMapper<PersistenceException> {

    private static final Logger LOG = Logger.getLogger(PersistenceConflictExceptionMapper.class);

    @Override
    public Response toResponse(PersistenceException exception) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        LOG.errorf(exception, "[%s] Persistence conflict: %s", errorId, exception.getMessage());
        return Response.status(Response.Status.CONFLICT)
            .entity(ErrorResponseDTO.from("CONFLICT", "Duplicate request [errorId=" + errorId + "]",
                List.of(new ErrorDetailDTO("idempotencyKey", "Duplicate key or constraint violation"))))
            .build();
    }
}
