package com.poc.gateway.exception;

import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.shared.dto.ErrorResponseDTO;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionMapperTest {

    @Mock
    ExceptionClassifier classifier;

    @InjectMocks
    GlobalExceptionMapper mapper;

    @Test
    void toResponse_clientErrorException_returnsClientStatus() {
        ClientErrorException clientError = new ClientErrorException(Response.Status.FORBIDDEN);
        Response response = mapper.toResponse(clientError);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(classifier);
    }

    @Test
    void toResponse_clientErrorException_returnsClientResponse() {
        Response expectedResponse = Response.status(409).entity("conflict").build();
        ClientErrorException clientError = new ClientErrorException(expectedResponse);
        Response response = mapper.toResponse(clientError);

        assertEquals(expectedResponse.getStatus(), response.getStatus());
    }

    @Test
    void toResponse_notFoundCategory_returns404() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.NOT_FOUND);
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-1");

        Response response = mapper.toResponse(ex);

        assertEquals(404, response.getStatus());
        verify(classifier).classify(ex);
    }

    @Test
    void toResponse_badRequestCategory_returns400() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.BAD_REQUEST);
        IllegalArgumentException ex = new IllegalArgumentException("bad input");

        Response response = mapper.toResponse(ex);

        assertEquals(400, response.getStatus());
    }

    @Test
    void toResponse_conflictCategory_returns409() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.CONFLICT);
        RuntimeException ex = new RuntimeException("duplicate key");

        Response response = mapper.toResponse(ex);

        assertEquals(409, response.getStatus());
    }

    @Test
    void toResponse_unauthorizedCategory_returns401() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.UNAUTHORIZED);
        RuntimeException ex = new RuntimeException("unauthorized");

        Response response = mapper.toResponse(ex);

        assertEquals(401, response.getStatus());
    }

    @Test
    void toResponse_forbiddenCategory_returns403() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.FORBIDDEN);
        RuntimeException ex = new RuntimeException("forbidden");

        Response response = mapper.toResponse(ex);

        assertEquals(403, response.getStatus());
    }

    @Test
    void toResponse_internalCategory_returns500() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.INTERNAL);
        RuntimeException ex = new RuntimeException("oops");

        Response response = mapper.toResponse(ex);

        assertEquals(500, response.getStatus());
    }

    @Test
    void toResponse_responseEntityContainsErrorDetail() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.NOT_FOUND);
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-1");

        Response response = mapper.toResponse(ex);

        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof ErrorResponseDTO);
        ErrorResponseDTO dto = (ErrorResponseDTO) response.getEntity();
        assertEquals("NOT_FOUND", dto.error());
        assertEquals("Payment not found: pay-1", dto.message());
        assertNotNull(dto.details());
        assertEquals(1, dto.details().size());
    }

    @Test
    void toResponse_responseEntityHasTimestamp() {
        when(classifier.classify(any())).thenReturn(ExceptionCategory.BAD_REQUEST);
        RuntimeException ex = new RuntimeException("bad");

        Response response = mapper.toResponse(ex);

        ErrorResponseDTO dto = (ErrorResponseDTO) response.getEntity();
        assertNotNull(dto.timestamp());
    }
}
