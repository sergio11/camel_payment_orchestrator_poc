package com.poc.gateway.adapter.inbound.rest;

import com.poc.gateway.application.service.CreatePaymentService;
import com.poc.gateway.application.service.GetPaymentService;
import com.poc.gateway.application.service.ListPaymentsService;
import com.poc.gateway.application.service.UpdatePaymentStatusService;
import com.poc.shared.dto.PaymentPageResponseDTO;
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

    @Test
    void create_returns201() {
        PaymentResponseDTO response = new PaymentResponseDTO(
            "uuid-1", BigDecimal.TEN, "USD", "cust", "CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(createPayment.execute(any(PaymentRequestDTO.class), any())).thenReturn(response);

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "key-123")
            .body("{\"amount\":10,\"currency\":\"USD\",\"customer_id\":\"cust\",\"payment_method\":\"CARD\",\"country\":\"US\"}")
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("id", org.hamcrest.Matchers.equalTo("uuid-1"));
    }

    @Test
    void getById_returns200() {
        PaymentResponseDTO response = new PaymentResponseDTO(
            "uuid-1", BigDecimal.TEN, "USD", "cust", "CARD", "US", "APPROVED", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(getPayment.execute("uuid-1")).thenReturn(response);

        given()
        .when()
            .get("/payments/uuid-1")
        .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("APPROVED"));
    }

    @Test
    void list_returns200() {
        PaymentPageResponseDTO page = new PaymentPageResponseDTO(List.of(), 0L, 20, 0);
        when(listPayments.execute(isNull(), isNull(), eq(20), eq(0))).thenReturn(page);

        given()
        .when()
            .get("/payments")
        .then()
            .statusCode(200);
    }

    @Test
    void updateStatus_returns200() {
        PaymentResponseDTO response = new PaymentResponseDTO(
            "uuid-1", BigDecimal.TEN, "USD", "cust", "CARD", "US", "APPROVED", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(updateStatus.execute("uuid-1", "APPROVED")).thenReturn(response);

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\":\"APPROVED\"}")
        .when()
            .patch("/payments/uuid-1/status")
        .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("APPROVED"));
    }

    @Test
    void getByIdempotencyKey_found_returns200() {
        PaymentResponseDTO response = new PaymentResponseDTO(
            "uuid-1", BigDecimal.TEN, "USD", "cust", "CARD", "US", "PENDING", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(getPayment.executeByIdempotencyKey("key-1")).thenReturn(Optional.of(response));

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
