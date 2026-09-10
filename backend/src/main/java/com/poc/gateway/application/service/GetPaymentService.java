package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.inbound.GetPaymentUseCase;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentResponseDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GetPaymentService implements GetPaymentUseCase {

    @Inject
    PaymentReadRepository paymentRepo;

    @Inject
    PaymentMapper mapper;

    @Override
    public PaymentResponseDTO execute(String paymentId) {
        if (paymentId == null) {
            throw new PaymentNotFoundException("null");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            throw new PaymentNotFoundException(paymentId);
        }
        Payment payment = paymentRepo.findById(uuid)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return mapper.toResponseDTO(payment);
    }

    @Override
    public Optional<PaymentResponseDTO> executeByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return paymentRepo.findByIdempotencyKey(idempotencyKey.trim())
            .map(mapper::toResponseDTO);
    }
}
