package com.poc.gateway.adapter.inbound.rest.presenter;

import com.poc.shared.dto.ErrorDetailDTO;
import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.List;

public class PaymentResponsePresenter {

    private PaymentResponsePresenter() {
    }

    public static Response buildNotFound(String message) {
        ErrorResponseDTO error = ErrorResponseDTO.from(
            "NOT_FOUND",
            message,
            List.of(new ErrorDetailDTO("id", message))
        );
        return Response.status(Response.Status.NOT_FOUND).entity(error).build();
    }

    public static Response buildBadRequest(String message) {
        ErrorResponseDTO error = ErrorResponseDTO.from(
            "BAD_REQUEST",
            message,
            List.of(new ErrorDetailDTO("request", message))
        );
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }

    public static Response buildConflict(String message) {
        ErrorResponseDTO error = ErrorResponseDTO.from(
            "CONFLICT",
            message,
            List.of(new ErrorDetailDTO("idempotency", message))
        );
        return Response.status(Response.Status.CONFLICT).entity(error).build();
    }

    public static Response buildInternalError(String message) {
        ErrorResponseDTO error = ErrorResponseDTO.from(
            "INTERNAL_ERROR",
            message,
            List.of(new ErrorDetailDTO("system", message))
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
    }
}