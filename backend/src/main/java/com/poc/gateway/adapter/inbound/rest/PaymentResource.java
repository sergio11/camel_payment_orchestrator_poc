package com.poc.gateway.adapter.inbound.rest;

import com.poc.gateway.domain.port.inbound.CreatePaymentUseCase;
import com.poc.gateway.domain.port.inbound.GetPaymentUseCase;
import com.poc.gateway.domain.port.inbound.ListPaymentsUseCase;
import com.poc.gateway.domain.port.inbound.UpdatePaymentStatusUseCase;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.PaymentPageResponseDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/payments")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payment Resource", description = "Payment CRUD operations")
public class PaymentResource {

    @Inject
    CreatePaymentUseCase createPayment;

    @Inject
    GetPaymentUseCase getPayment;

    @Inject
    ListPaymentsUseCase listPayments;

    @Inject
    UpdatePaymentStatusUseCase updateStatus;

    @POST
    @Operation(summary = "Create a new payment", description = "Creates a new payment with idempotency support")
    public Response create(
            PaymentRequestDTO request,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {
        PaymentResponseDTO result = createPayment.execute(request, idempotencyKey);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get payment by ID", description = "Retrieves a payment by its unique identifier")
    public Response getById(@jakarta.ws.rs.PathParam("id") String id) {
        PaymentResponseDTO result = getPayment.execute(id);
        return Response.ok(result).build();
    }

    @GET
    @Operation(summary = "List payments", description = "Lists payments with optional filters and pagination")
    public Response list(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        PaymentPageResponseDTO result = listPayments.execute(customerId, status, limit, offset);
        return Response.ok(result).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Operation(summary = "Update payment status", description = "Updates the status of an existing payment")
    public Response updateStatus(
            @jakarta.ws.rs.PathParam("id") String id,
            Map<String, String> body) {
        String newStatus = body.get("status");
        PaymentResponseDTO result = updateStatus.execute(id, newStatus);
        return Response.ok(result).build();
    }

    @GET
    @Path("/idempotency/{key}")
    @Operation(summary = "Get payment by idempotency key", description = "Retrieves a payment by its idempotency key")
    public Response getByIdempotencyKey(@jakarta.ws.rs.PathParam("key") String key) {
        return getPayment.executeByIdempotencyKey(key)
            .map(result -> Response.ok(result).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}