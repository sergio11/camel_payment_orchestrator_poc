package com.poc.gateway.resource;

import com.poc.shared.dto.ErrorResponse;
import com.poc.shared.dto.PaymentPageResponse;
import com.poc.shared.dto.PaymentRequest;
import com.poc.shared.dto.PaymentResponse;
import com.poc.shared.dto.PaymentStatusResponse;
import com.poc.gateway.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import io.smallrye.common.annotation.Blocking;
import org.jboss.logging.Logger;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    @Inject
    PaymentService paymentService;

    @POST
    @Blocking
    public Response createPayment(@Valid PaymentRequest request, @HeaderParam("Idempotency-Key") String idempotencyKey) {
        String effectiveKey = idempotencyKey;
        if (effectiveKey == null || effectiveKey.isBlank()) {
            effectiveKey = java.util.UUID.randomUUID().toString();
        } else {
            effectiveKey = effectiveKey.trim();
            try {
                java.util.UUID.fromString(effectiveKey);
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.from("VALIDATION_ERROR", "Invalid Idempotency-Key header, must be UUID",
                        List.of(new ErrorResponse.ErrorDetail("Idempotency-Key", "Must be a valid UUID"))))
                    .build();
            }
        }
        boolean replay = false;
        try {
            replay = paymentService.getByIdempotencyKey(effectiveKey).isPresent();
        } catch (Exception e) {
            LOG.warnf(e, "Idempotency pre-check failed for key %s (best-effort)", effectiveKey);
        }
        LOG.infof("Creating payment for customer: %s, amount: %s %s", request.customerId(), request.amount(), request.currency());
        PaymentResponse response = paymentService.createPayment(request, effectiveKey);
        LOG.infof("Payment created: %s", response.id());
        return Response.status(replay ? Response.Status.OK : Response.Status.CREATED)
            .header("Idempotency-Key", effectiveKey)
            .entity(response)
            .build();
    }

    @GET
    public Response listPayments(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {

        if (limit < 0) limit = 20;
        if (limit > 100) limit = 100;
        if (offset < 0) offset = 0;

        List<PaymentResponse> payments = paymentService.listPayments(customerId, status, limit, offset);
        long total = paymentService.countPayments(customerId, status);
        return Response.ok(new PaymentPageResponse(payments, total, limit, offset)).build();
    }

    @GET
    @Path("/{id}")
    public Response getPayment(@PathParam("id") String id) {
        PaymentResponse response = paymentService.getPayment(id);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}/status")
    public Response getPaymentStatus(@PathParam("id") String id) {
        PaymentResponse response = paymentService.getPayment(id);
        PaymentStatusResponse statusResponse = new PaymentStatusResponse(
            response.id(),
            response.status(),
            response.updatedAt()
        );
        return Response.ok(statusResponse).build();
    }
}
