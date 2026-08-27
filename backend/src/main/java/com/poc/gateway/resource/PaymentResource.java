package com.poc.gateway.resource;

import com.poc.shared.dto.ErrorResponse;
import com.poc.shared.dto.PaymentPageResponse;
import com.poc.shared.dto.PaymentRequest;
import com.poc.shared.dto.PaymentResponse;
import com.poc.shared.dto.PaymentStatusResponse;
import com.poc.gateway.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.smallrye.common.annotation.Blocking;
import org.jboss.logging.Logger;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);
    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    @Inject
    PaymentService paymentService;

    @POST
    @Blocking
    public Response createPayment(PaymentRequest request) {
        Set<jakarta.validation.ConstraintViolation<PaymentRequest>> violations = VALIDATOR.validate(request);
        if (!violations.isEmpty()) {
            List<ErrorResponse.ErrorDetail> details = violations.stream()
                .map(v -> new ErrorResponse.ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .collect(Collectors.toList());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.from("VALIDATION_ERROR", "Request validation failed", details))
                .build();
        }

        LOG.infof("Creating payment for customer: %s, amount: %s %s", request.customerId(), request.amount(), request.currency());
        PaymentResponse response = paymentService.createPayment(request);
        LOG.infof("Payment created: %s", response.id());
        return Response.status(Response.Status.CREATED)
            .entity(response)
            .build();
    }

    @GET
    public Response listPayments(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") @Min(0) int limit,
            @QueryParam("offset") @DefaultValue("0") @Min(0) int offset) {

        if (limit < 0) limit = 20;
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
