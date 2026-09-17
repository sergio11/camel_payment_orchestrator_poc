package com.poc.gateway.adapter.inbound.rest;

import com.poc.gateway.application.mapper.PaymentMapper;
import com.poc.gateway.application.service.CreatePaymentService;
import com.poc.gateway.application.service.GetPaymentService;
import com.poc.gateway.application.service.ListPaymentsService;
import com.poc.gateway.application.service.UpdatePaymentStatusService;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.gateway.domain.model.CreatePaymentCommand;
import com.poc.gateway.domain.model.PaymentPageResult;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.inbound.CreatePaymentUseCase;
import com.poc.gateway.domain.port.inbound.GetPaymentUseCase;
import com.poc.gateway.domain.port.inbound.ListPaymentsUseCase;
import com.poc.gateway.domain.port.inbound.UpdatePaymentStatusUseCase;
import com.poc.shared.dto.PaymentPageResponseDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import com.poc.shared.dto.UpdatePaymentStatusRequestDTO;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentResourceUnitTest {

    @Mock
    CreatePaymentUseCase createPayment;

    @Mock
    GetPaymentUseCase getPayment;

    @Mock
    ListPaymentsUseCase listPayments;

    @Mock
    UpdatePaymentStatusUseCase updateStatus;

    @Mock
    PaymentMapper mapper;

    @InjectMocks
    PaymentResource resource;

    private Payment domainPayment;
    private PaymentResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        UUID paymentId = UUID.randomUUID();
        domainPayment = new Payment(
            paymentId, BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentStatus.PENDING,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        responseDTO = new PaymentResponseDTO(
            paymentId.toString(), BigDecimal.TEN, "USD", "cust", "CARD", "US", "PENDING",
            null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("create() should return 201 with payment response")
    void testCreate() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentMetadataDTO.empty()
        );
        CreatePaymentCommand command = new CreatePaymentCommand(
            BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentMetadata.empty()
        );

        when(mapper.toCommand(any(PaymentRequestDTO.class))).thenReturn(command);
        when(createPayment.execute(any(CreatePaymentCommand.class), eq("key-123"))).thenReturn(domainPayment);
        when(mapper.toResponseDTO(any(Payment.class))).thenReturn(responseDTO);

        Response response = resource.create(request, "key-123");

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
        verify(createPayment).execute(any(CreatePaymentCommand.class), eq("key-123"));
    }

    @Test
    @DisplayName("getById() should return 200 with payment")
    void testGetById() {
        UUID paymentId = domainPayment.id();
        when(getPayment.execute(paymentId)).thenReturn(domainPayment);
        when(mapper.toResponseDTO(domainPayment)).thenReturn(responseDTO);

        Response response = resource.getById(paymentId.toString());

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    @DisplayName("list() should return 200 with page")
    void testList() {
        PaymentPageResult pageResult = new PaymentPageResult(List.of(), 0L, 20, 0);
        when(listPayments.execute(isNull(), isNull(), eq(20), eq(0))).thenReturn(pageResult);

        Response response = resource.list(null, null, 20, 0);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    @DisplayName("list() should reset limit below 1 to 20")
    void testListInvalidLimit() {
        PaymentPageResult pageResult = new PaymentPageResult(List.of(), 0L, 20, 0);
        when(listPayments.execute(isNull(), isNull(), eq(20), eq(0))).thenReturn(pageResult);

        Response response = resource.list(null, null, 0, 0);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("list() should cap limit above 100 to 100")
    void testListLimitExceeding100() {
        PaymentPageResult pageResult = new PaymentPageResult(List.of(), 0L, 100, 0);
        when(listPayments.execute(isNull(), isNull(), eq(100), eq(0))).thenReturn(pageResult);

        Response response = resource.list(null, null, 500, 0);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("list() should reset negative offset to 0")
    void testListNegativeOffset() {
        PaymentPageResult pageResult = new PaymentPageResult(List.of(), 0L, 20, 0);
        when(listPayments.execute(isNull(), isNull(), eq(20), eq(0))).thenReturn(pageResult);

        Response response = resource.list(null, null, 20, -1);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("updateStatus() should return 200 with updated payment")
    void testUpdateStatus() {
        UUID paymentId = domainPayment.id();
        Payment updatedPayment = new Payment(
            paymentId, BigDecimal.TEN, "USD", "cust", "CARD", "US", PaymentStatus.APPROVED,
            null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now()
        );
        PaymentResponseDTO updatedResponse = new PaymentResponseDTO(
            paymentId.toString(), BigDecimal.TEN, "USD", "cust", "CARD", "US", "APPROVED",
            null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(updateStatus.execute(paymentId, PaymentStatus.APPROVED)).thenReturn(updatedPayment);
        when(mapper.toResponseDTO(updatedPayment)).thenReturn(updatedResponse);

        UpdatePaymentStatusRequestDTO body = new UpdatePaymentStatusRequestDTO("APPROVED");
        Response response = resource.updateStatus(paymentId.toString(), body);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    @DisplayName("getByIdempotencyKey() should return 200 when found")
    void testGetByIdempotencyKey_found() {
        when(getPayment.executeByIdempotencyKey("key-1")).thenReturn(Optional.of(domainPayment));
        when(mapper.toResponseDTO(domainPayment)).thenReturn(responseDTO);

        Response response = resource.getByIdempotencyKey("key-1");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    @DisplayName("getByIdempotencyKey() should return 404 when not found")
    void testGetByIdempotencyKey_notFound() {
        when(getPayment.executeByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        Response response = resource.getByIdempotencyKey("key-1");

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
