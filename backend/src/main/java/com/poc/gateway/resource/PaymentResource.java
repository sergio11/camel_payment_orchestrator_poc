package com.poc.gateway.resource;

import com.poc.shared.dto.ErrorResponse;
import com.poc.shared.dto.PaymentPageResponse;
import com.poc.shared.dto.PaymentRequest;
import com.poc.shared.dto.PaymentResponse;
import com.poc.shared.dto.PaymentStatusResponse;
import com.poc.gateway.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import io.smallrye.common.annotation.Blocking;
import org.jboss.logging.Logger;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);
    private static final Pattern VALID_PAYMENT_METHOD = Pattern.compile("CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|CRYPTO");
    private static final List<String> SUPPORTED_CURRENCIES = List.of("USD", "EUR", "GBP", "MXN", "JPY");

    @Inject
    PaymentService paymentService;

    @POST
    @Blocking
    public Response createPayment(PaymentRequest request) {
        List<ErrorResponse.ErrorDetail> errors = validatePaymentRequest(request);
        if (!errors.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.from("VALIDATION_ERROR", "Invalid payment request", errors))
                .build();
        }
        LOG.infof("Creating payment for customer: %s, amount: %s %s", request.customerId(), request.amount(), request.currency());
        PaymentResponse response = paymentService.createPayment(request);
        LOG.infof("Payment created: %s", response.id());
        return Response.status(Response.Status.CREATED)
            .entity(response)
            .build();
    }

    private List<ErrorResponse.ErrorDetail> validatePaymentRequest(PaymentRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new ErrorResponse.ErrorDetail("body", "Request body is required"));
            return errors;
        }
        if (request.amount() == null) {
            errors.add(new ErrorResponse.ErrorDetail("amount", "Amount is required"));
        } else {
            if (request.amount().compareTo(new BigDecimal("0.01")) < 0) {
                errors.add(new ErrorResponse.ErrorDetail("amount", "Amount must be >= 0.01"));
            }
            if (request.amount().compareTo(new BigDecimal("999999.99")) > 0) {
                errors.add(new ErrorResponse.ErrorDetail("amount", "Amount must be <= 999999.99"));
            }
        }
        if (request.currency() == null || request.currency().isBlank()) {
            errors.add(new ErrorResponse.ErrorDetail("currency", "Currency is required"));
        } else if (!SUPPORTED_CURRENCIES.contains(request.currency())) {
            errors.add(new ErrorResponse.ErrorDetail("currency", "Unsupported currency. Allowed: USD, EUR, GBP, MXN, JPY"));
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            errors.add(new ErrorResponse.ErrorDetail("customerId", "Customer ID is required"));
        } else if (request.customerId().length() > 50) {
            errors.add(new ErrorResponse.ErrorDetail("customerId", "Customer ID max 50 characters"));
        }
        if (request.paymentMethod() == null) {
            errors.add(new ErrorResponse.ErrorDetail("paymentMethod", "Payment method is required"));
        } else if (!VALID_PAYMENT_METHOD.matcher(request.paymentMethod()).matches()) {
            errors.add(new ErrorResponse.ErrorDetail("paymentMethod", "Invalid payment method"));
        }
        if (request.country() != null && request.country().length() > 2) {
            errors.add(new ErrorResponse.ErrorDetail("country", "Country code max 2 characters"));
        }
        return errors;
    }

    @GET
    public Response listPayments(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") @Min(0) int limit,
            @QueryParam("offset") @DefaultValue("0") @Min(0) int offset) {

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
