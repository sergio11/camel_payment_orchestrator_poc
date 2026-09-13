package com.poc.gateway.adapter.inbound.rest;

import com.poc.gateway.application.mapper.PaymentMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.CreatePaymentCommand;
import com.poc.gateway.domain.model.PaymentPageResult;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.inbound.CreatePaymentUseCase;
import com.poc.gateway.domain.port.inbound.GetPaymentUseCase;
import com.poc.gateway.domain.port.inbound.ListPaymentsUseCase;
import com.poc.gateway.domain.port.inbound.UpdatePaymentStatusUseCase;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.PaymentPageResponseDTO;
import com.poc.shared.dto.UpdatePaymentStatusRequestDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
import java.util.UUID;
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

    @Inject
    PaymentMapper mapper;

    @POST
    @Operation(summary = "Create a new payment", description = "Creates a new payment with idempotency support")
    public Response create(
            @Valid PaymentRequestDTO request,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {
        CreatePaymentCommand command = mapper.toCommand(request);
        Payment result = createPayment.execute(command, idempotencyKey);
        PaymentResponseDTO response = mapper.toResponseDTO(result);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get payment by ID", description = "Retrieves a payment by its unique identifier")
    public Response getById(@jakarta.ws.rs.PathParam("id") String id) {
        UUID uuid = UUID.fromString(id);
        Payment result = getPayment.execute(uuid);
        PaymentResponseDTO response = mapper.toResponseDTO(result);
        return Response.ok(response).build();
    }

    @GET
    @Operation(summary = "List payments", description = "Lists payments with optional filters and pagination")
    public Response list(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        if (limit < 1) limit = 20;
        if (offset < 0) offset = 0;
        PaymentStatus paymentStatus = PaymentStatus.fromString(status).orElse(null);
        PaymentPageResult result = listPayments.execute(customerId, paymentStatus, limit, offset);
        java.util.List<PaymentResponseDTO> payments = result.payments().stream()
            .map(mapper::toResponseDTO)
            .toList();
        PaymentPageResponseDTO response = new PaymentPageResponseDTO(payments, result.total(), result.limit(), result.offset());
        return Response.ok(response).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Operation(summary = "Update payment status", description = "Updates the status of an existing payment")
    public Response updateStatus(
            @jakarta.ws.rs.PathParam("id") String id,
            @Valid UpdatePaymentStatusRequestDTO body) {
        UUID uuid = UUID.fromString(id);
        PaymentStatus newStatus = PaymentStatus.fromString(body.status())
            .orElseThrow(() -> new IllegalArgumentException("Invalid status: " + body.status()));
        Payment result = updateStatus.execute(uuid, newStatus);
        PaymentResponseDTO response = mapper.toResponseDTO(result);
        return Response.ok(response).build();
    }

    @GET
    @Path("/idempotency/{key}")
    @Operation(summary = "Get payment by idempotency key", description = "Retrieves a payment by its idempotency key")
    public Response getByIdempotencyKey(@jakarta.ws.rs.PathParam("key") String key) {
        return getPayment.executeByIdempotencyKey(key)
            .map(payment -> {
                PaymentResponseDTO response = mapper.toResponseDTO(payment);
                return Response.ok(response).build();
            })
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
