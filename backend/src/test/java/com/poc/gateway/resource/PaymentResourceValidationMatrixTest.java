package com.poc.gateway.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class PaymentResourceValidationMatrixTest {

    @InjectMock
    com.poc.gateway.service.KafkaEventPublisher kafkaEventPublisher;

    @BeforeEach
    void stubPublisher() {
        Mockito.clearInvocations(kafkaEventPublisher);
        when(kafkaEventPublisher.publishPaymentReceived(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(true);
    }

    private static String body(String amount, String currency, String customerId, String paymentMethod, String countryJson) {
        return """
            {
                "amount": %s,
                "currency": "%s",
                "customerId": "%s",
                "paymentMethod": "%s"%s
            }
            """.formatted(amount, currency, customerId, paymentMethod, countryJson);
    }

    private static String withCountry(String country) {
        return ",\n                \"country\": \"%s\"".formatted(country);
    }

    @ParameterizedTest(name = "valid currency {0} returns 2xx never 500")
    @ValueSource(strings = {"USD", "EUR", "GBP", "MXN", "JPY"})
    void createPayment_withSupportedCurrency_returns201or200(String currency) {
        given()
            .contentType(ContentType.JSON)
            .body(body("100.00", currency, "cust-matrix-" + currency, "CREDIT_CARD", withCountry("US")))
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }

    @ParameterizedTest(name = "invalid currency [{0}] returns 400 never 500")
    @ValueSource(strings = {"BTC", "usd", ""})
    void createPayment_withUnsupportedCurrency_returns400(String currency) {
        given()
            .contentType(ContentType.JSON)
            .body(body("100.00", currency, "cust-matrix-inv", "CREDIT_CARD", withCountry("US")))
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @ParameterizedTest(name = "valid country {0} returns 2xx")
    @ValueSource(strings = {"US", "ES"})
    void createPayment_withValidCountry_returns201or200(String country) {
        given()
            .contentType(ContentType.JSON)
            .body(body("100.00", "USD", "cust-matrix-c-" + country, "CREDIT_CARD", withCountry(country)))
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }

    @ParameterizedTest(name = "invalid country [{0}] returns 400")
    @ValueSource(strings = {"USA", "U1", ""})
    void createPayment_withInvalidCountry_returns400(String country) {
        given()
            .contentType(ContentType.JSON)
            .body(body("100.00", "USD", "cust-matrix-cinv", "CREDIT_CARD", withCountry(country)))
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_withNullCountry_returns201or200() {
        String noCountry = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "cust-matrix-null-c",
                "paymentMethod": "CREDIT_CARD"
            }
            """;
        given()
            .contentType(ContentType.JSON)
            .body(noCountry)
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }

    @Test
    void createPayment_withExplicitNullCountry_returns201or200() {
        String nullCountry = """
            {
                "amount": 100.00,
                "currency": "USD",
                "customerId": "cust-matrix-null-c2",
                "paymentMethod": "CREDIT_CARD",
                "country": null
            }
            """;
        given()
            .contentType(ContentType.JSON)
            .body(nullCountry)
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }

    @ParameterizedTest(name = "invalid amount {0} returns 400")
    @ValueSource(strings = {"0.00", "-5", "1000000.00"})
    void createPayment_withOutOfRangeAmount_returns400(String amount) {
        given()
            .contentType(ContentType.JSON)
            .body(body(amount, "USD", "cust-matrix-amt", "CREDIT_CARD", withCountry("US")))
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @ParameterizedTest(name = "boundary amount {0} returns 2xx")
    @ValueSource(strings = {"0.01", "999999.99"})
    void createPayment_withBoundaryAmount_returns201or200(String amount) {
        given()
            .contentType(ContentType.JSON)
            .body(body(amount, "USD", "cust-matrix-bound-" + amount.replace(".", "_"), "CREDIT_CARD", withCountry("US")))
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }

    @Test
    void createPayment_withSameIdempotencyKey_secondIsReplayWithSameId() {
        String key = UUID.randomUUID().toString();
        String payload = body("50.00", "USD", "cust-matrix-idem", "CREDIT_CARD", withCountry("US"));

        String firstId = given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body(payload)
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("id", org.hamcrest.Matchers.notNullValue())
            .extract()
            .path("id");

        String secondId = given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body(payload)
        .when()
            .post("/payments")
        .then()
            .statusCode(200)
            .body("id", equalTo(firstId))
            .extract()
            .path("id");

        assertEquals(firstId, secondId);
        verify(kafkaEventPublisher, times(1)).publishPaymentReceived(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createPayment_withDistinctIdempotencyKeys_createsDistinctPayments() {
        String payload = body("50.00", "USD", "cust-matrix-idem2", "CREDIT_CARD", withCountry("US"));

        String id1 = given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(payload)
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        String id2 = given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(payload)
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        assertNotEquals(id1, id2);
    }

    @Test
    void createPayment_withInvalidIdempotencyKey_returns400() {
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "not-a-uuid")
            .body(body("50.00", "USD", "cust-matrix-idem3", "CREDIT_CARD", withCountry("US")))
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    void createPayment_withUnknownPaymentMethod_passesBeanValidation() {
        // Known limitation (not a test bug): PaymentRequest.paymentMethod only has
        // @NotNull, no enum/pattern constraint, so unknown values like BITCOIN pass
        // bean validation and reach 201/200. Method allow-list is enforced elsewhere.
        given()
            .contentType(ContentType.JSON)
            .body(body("50.00", "USD", "cust-matrix-pm", "BITCOIN", withCountry("US")))
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(201), is(200)));
    }
}
