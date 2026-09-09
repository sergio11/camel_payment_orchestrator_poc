package com.poc.gateway.resource;

import com.poc.gateway.resource.presenter.PaymentResponsePresenter;
import com.poc.gateway.service.PaymentService;
import com.poc.shared.dto.PaymentPageResponseDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.PaymentStatusResponseDTO;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

import static java.util.UUID.randomUUID;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentResponsePresenter presenter;

    @POST
    @Blocking
    public Response createPayment(@Valid PaymentRequestDTO request, @HeaderParam("Idempotency-Key") String idempotencyKey) {
        if (request == null) {
            return presenter.invalidRequestBody();
        }
        String effectiveKey = idempotencyKey;
        if (effectiveKey == null || effectiveKey.isBlank()) {
            effectiveKey = randomUUID().toString();
        } else {
            effectiveKey = effectiveKey.trim();
            try {
                UUID.fromString(effectiveKey);
            } catch (IllegalArgumentException e) {
                return presenter.invalidIdempotencyKey();
            }
        }
        boolean replay = false;
        try {
            replay = paymentService.getByIdempotencyKey(effectiveKey).isPresent();
        } catch (Exception e) {
            LOG.warnf(e, "Idempotency pre-check failed for key %s (best-effort)", effectiveKey);
        }
        LOG.infof("Creating payment for customer: %s, amount: %s %s", request.customerId(), request.amount(), request.currency());
        PaymentResponseDTO response = paymentService.createPayment(request, effectiveKey);
        LOG.infof("Payment created: %s", response.id());
        return presenter.paymentCreated(response, effectiveKey, replay);
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

        List<PaymentResponseDTO> payments = paymentService.listPayments(customerId, status, limit, offset);
        long total = paymentService.countPayments(customerId, status);
        return presenter.paymentPage(new PaymentPageResponseDTO(payments, total, limit, offset));
    }

    @GET
    @Path("/{id}")
    public Response getPayment(@PathParam("id") String id) {
        PaymentResponseDTO response = paymentService.getPayment(id);
        return presenter.paymentDetail(response);
    }

    @GET
    @Path("/{id}/status")
    public Response getPaymentStatus(@PathParam("id") String id) {
        PaymentResponseDTO response = paymentService.getPayment(id);
        PaymentStatusResponseDTO statusResponse = new PaymentStatusResponseDTO(
            response.id(),
            response.status(),
            response.updatedAt()
        );
        return presenter.paymentStatus(statusResponse);
    }
}
