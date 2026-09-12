package com.poc.gateway.adapter.inbound.rest;

import com.poc.gateway.application.mapper.PaymentMapper;
import com.poc.gateway.application.service.CreatePaymentService;
import com.poc.gateway.application.service.GetPaymentService;
import com.poc.gateway.application.service.ListPaymentsService;
import com.poc.gateway.application.service.UpdatePaymentStatusService;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentPageResult;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@QuarkusTest
class PaymentResourceTest {

    @InjectMock
    CreatePaymentService createPayment;

    @InjectMock
    GetPaymentService getPayment;

    @InjectMock
    ListPaymentsService listPayments;

    @InjectMock
    UpdatePaymentStatusService updateStatus;

    @InjectMock
    PaymentMapper mapper;

    @Test
    void create_returns201() {
        UUID paymentId = UUID.randomUUID();
        Payment domainPayment = new Payment(
            paymentId, BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        PaymentResponseDTO response = new PaymentResponseDTO(
            paymentId.toString(), BigDecimal.TEN, "USD", "cust", "CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(createPayment.execute(any(PaymentRequestDTO.class), any())).thenReturn(domainPayment);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(response);

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "key-123")
            .body("{\"amount\":10,\"currency\":\"USD\",\"customer_id\":\"cust\",\"payment_method\":\"CARD\",\"country\":\"US\"}")
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("id", org.hamcrest.Matchers.equalTo(paymentId.toString()));
    }

    @Test
    void getById_returns200() {
        UUID paymentId = UUID.randomUUID();
        Payment domainPayment = new Payment(
            paymentId, BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentStatus.APPROVED,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        PaymentResponseDTO response = new PaymentResponseDTO(
            paymentId.toString(), BigDecimal.TEN, "USD", "cust", "CARD", "US", "APPROVED", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(getPayment.execute(paymentId)).thenReturn(domainPayment);
        when(mapper.toResponseDTO(domainPayment)).thenReturn(response);

        given()
        .when()
            .get("/payments/" + paymentId)
        .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("APPROVED"));
    }

    @Test
    void list_returns200() {
        PaymentPageResult pageResult = new PaymentPageResult(List.of(), 0L, 20, 0);
        when(listPayments.execute(isNull(), isNull(), eq(20), eq(0))).thenReturn(pageResult);

        given()
        .when()
            .get("/payments")
        .then()
            .statusCode(200);
    }

    @Test
    void updateStatus_returns200() {
        UUID paymentId = UUID.randomUUID();
        Payment domainPayment = new Payment(
            paymentId, BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentStatus.APPROVED,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        PaymentResponseDTO response = new PaymentResponseDTO(
            paymentId.toString(), BigDecimal.TEN, "USD", "cust", "CARD", "US", "APPROVED", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(updateStatus.execute(paymentId, PaymentStatus.APPROVED)).thenReturn(domainPayment);
        when(mapper.toResponseDTO(domainPayment)).thenReturn(response);

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\":\"APPROVED\"}")
        .when()
            .patch("/payments/" + paymentId + "/status")
        .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("APPROVED"));
    }

    @Test
    void getByIdempotencyKey_found_returns200() {
        UUID paymentId = UUID.randomUUID();
        Payment domainPayment = new Payment(
            paymentId, BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        PaymentResponseDTO response = new PaymentResponseDTO(
            paymentId.toString(), BigDecimal.TEN, "USD", "cust", "CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(getPayment.executeByIdempotencyKey("key-1")).thenReturn(Optional.of(domainPayment));
        when(mapper.toResponseDTO(domainPayment)).thenReturn(response);

        given()
        .when()
            .get("/payments/idempotency/key-1")
        .then()
            .statusCode(200);
    }

    @Test
    void getByIdempotencyKey_notFound_returns404() {
        when(getPayment.executeByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        given()
        .when()
            .get("/payments/idempotency/key-1")
        .then()
            .statusCode(404);
    }
}
