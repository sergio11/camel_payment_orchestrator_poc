package com.poc.gateway.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentResourceTest {

    @InjectMock
    com.poc.gateway.service.KafkaEventPublisher kafkaEventPublisher;

    private static final String VALID_BODY = """
        {
            "amount": 150.00,
            "currency": "USD",
            "customerId": "cust-test-001",
            "paymentMethod": "CREDIT_CARD",
            "country": "US",
            "metadata": {"channel": "api"}
        }
        """;

    private static String createdPaymentId;

    @BeforeEach
    void stubPublisher() {
        Mockito.clearInvocations(kafkaEventPublisher);
        when(kafkaEventPublisher.publishPaymentReceived(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(true);
    }

    @Test
    @Order(1)
    void createPayment_returns201WithPaymentResponse() {

        createdPaymentId = given()
            .contentType(ContentType.JSON)
            .body(VALID_BODY)
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("amount", equalTo(150.00f))
            .body("currency", equalTo("USD"))
            .body("customerId", equalTo("cust-test-001"))
            .body("paymentMethod", equalTo("CREDIT_CARD"))
            .body("country", equalTo("US"))
            .body("status", equalTo("PENDING"))
            .body("metadata.channel", equalTo("api"))
            .extract()
            .path("id");
    }

    @Test
    @Order(2)
    void getPaymentById_returns200WithPayment() {
        given()
            .pathParam("id", createdPaymentId)
        .when()
            .get("/payments/{id}")
        .then()
            .statusCode(200)
            .body("id", equalTo(createdPaymentId))
            .body("amount", equalTo(150.00f))
            .body("currency", equalTo("USD"))
            .body("status", equalTo("PENDING"));
    }

    @Test
    @Order(3)
    void getPaymentById_returns404ForNonexistentId() {
        String fakeId = UUID.randomUUID().toString();

        given()
            .pathParam("id", fakeId)
        .when()
            .get("/payments/{id}")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(4)
    void getPaymentStatus_returns200WithStatusResponse() {
        given()
            .pathParam("id", createdPaymentId)
        .when()
            .get("/payments/{id}/status")
        .then()
            .statusCode(200)
            .body("id", equalTo(createdPaymentId))
            .body("status", equalTo("PENDING"))
            .body("lastUpdated", notNullValue());
    }

    @Test
    @Order(5)
    void listPayments_returns200WithPayments() {
        given()
            .queryParam("limit", 10)
            .queryParam("offset", 0)
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("payments.id", hasItem(createdPaymentId))
            .body("total", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(6)
    void listPayments_filtersByCustomerId() {
        given()
            .queryParam("customerId", "cust-test-001")
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("payments", hasSize(greaterThanOrEqualTo(1)))
            .body("payments.customerId", everyItem(equalTo("cust-test-001")));
    }

    @Test
    @Order(7)
    void listPayments_filtersByStatus() {
        given()
            .queryParam("status", "PENDING")
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("payments.status", everyItem(equalTo("PENDING")));
    }

    @Test
    void createPayment_returns400ForMissingAmount() {
        String body = """
            {
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_unknownPaymentMethod_passesBeanValidation() {
        String body = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": "INVALID_METHOD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }

    @Test
    void createPayment_returns400ForMissingCurrency() {
        String body = """
            {
                "amount": 100.00,
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void testListPaymentsWithInvalidStatusReturns400() {
        given()
            .queryParam("status", "INVALID_STATUS")
        .when()
            .get("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void listPayments_withLimitAbove100_returnsMax100() {
        given()
            .queryParam("limit", 1000)
            .queryParam("offset", 0)
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("limit", equalTo(100));
    }

    @Test
    void listPayments_withLimit100_returns100OrLess() {
        given()
            .queryParam("limit", 100)
            .queryParam("offset", 0)
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("limit", equalTo(100));
    }

    @Test
    void getPaymentById_withInvalidUUID_returns404() {
        given()
            .pathParam("id", "not-a-valid-uuid")
        .when()
            .get("/payments/{id}")
        .then()
            .statusCode(404);
    }

    @Test
    void createPayment_returns400ForNegativeAmount() {
        String body = """
            {
                "amount": -10.00,
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_kafkaFails_returns500() {
        Mockito.clearInvocations(kafkaEventPublisher);
        when(kafkaEventPublisher.publishPaymentReceived(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(false);

        given()
            .contentType(ContentType.JSON)
            .body(VALID_BODY)
        .when()
            .post("/payments")
        .then()
            .statusCode(500);
    }

    @Test
    void createPayment_nullBody_returns400() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_emptyCurrency_returns400() {
        String body = """
            {
                "amount": 100.00,
                "currency": "",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_unsupportedCurrency_returns400() {
        String body = """
            {
                "amount": 100.00,
                "currency": "BTC",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_amountTooHigh_returns400() {
        String body = """
            {
                "amount": 1000000.00,
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_amountTooLow_returns400() {
        String body = """
            {
                "amount": 0.00,
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_emptyCustomerId_returns400() {
        String body = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "",
                "paymentMethod": "CREDIT_CARD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_countryTooLong_returns400() {
        String body = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": "CREDIT_CARD",
                "country": "USA"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_customerIdTooLong_returns400() {
        String longCustomerId = "a".repeat(51);
        String body = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "%s",
                "paymentMethod": "CREDIT_CARD",
                "country": "US"
            }
            """.formatted(longCustomerId);

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void listPayments_withNegativeLimit_returnsDefault() {
        given()
            .queryParam("limit", -1)
            .queryParam("offset", 0)
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("limit", equalTo(20));
    }

    @Test
    void listPayments_withNegativeOffset_returnsZero() {
        given()
            .queryParam("limit", 10)
            .queryParam("offset", -5)
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("offset", equalTo(0));
    }

    @Test
    void createPayment_nullCustomerId_returns400() {
        String body = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": null,
                "paymentMethod": "CREDIT_CARD",
                "country": "US"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_nullPaymentMethod_returns400() {
        String body = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "cust-1",
                "paymentMethod": null,
                "country": "US"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }
}
