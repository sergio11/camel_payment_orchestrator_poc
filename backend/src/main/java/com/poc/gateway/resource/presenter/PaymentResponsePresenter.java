package com.poc.gateway.resource.presenter;

import com.poc.shared.dto.ErrorDetailDTO;
import com.poc.shared.dto.ErrorResponseDTO;
import com.poc.shared.dto.PaymentPageResponseDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.PaymentStatusResponseDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.List;

@ApplicationScoped
public class PaymentResponsePresenter {

    public Response badRequest(String code, String message, List<ErrorDetailDTO> details) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponseDTO.from(code, message, details))
            .build();
    }

    public Response invalidRequestBody() {
        return badRequest("VALIDATION_ERROR", "Invalid request",
            List.of(new ErrorDetailDTO("body", "Request body is required")));
    }

    public Response invalidIdempotencyKey() {
        return badRequest("VALIDATION_ERROR", "Invalid Idempotency-Key header, must be UUID",
            List.of(new ErrorDetailDTO("Idempotency-Key", "Must be a valid UUID")));
    }

    public Response paymentCreated(PaymentResponseDTO response, String idempotencyKey, boolean replay) {
        return Response.status(replay ? Response.Status.OK : Response.Status.CREATED)
            .header("Idempotency-Key", idempotencyKey)
            .entity(response)
            .build();
    }

    public Response paymentPage(PaymentPageResponseDTO page) {
        return Response.ok(page).build();
    }

    public Response paymentDetail(PaymentResponseDTO response) {
        return Response.ok(response).build();
    }

    public Response paymentStatus(PaymentStatusResponseDTO statusResponse) {
        return Response.ok(statusResponse).build();
    }
}
