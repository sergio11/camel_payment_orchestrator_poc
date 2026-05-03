package com.poc.gateway.resource;

import com.poc.gateway.dto.PaymentRequest;
import com.poc.gateway.dto.PaymentResponse;
import com.poc.gateway.dto.PaymentStatusResponse;
import com.poc.gateway.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentService paymentService;

    @POST
    public Response createPayment(@Valid PaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return Response.status(Response.Status.CREATED)
            .entity(response)
            .build();
    }

    @GET
    public Response listPayments(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        
        List<PaymentResponse> payments = paymentService.listPayments(customerId, status, limit, offset);
        return Response.ok(payments).build();
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