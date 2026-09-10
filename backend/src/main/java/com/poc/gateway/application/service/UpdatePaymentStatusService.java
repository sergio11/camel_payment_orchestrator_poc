package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.inbound.UpdatePaymentStatusUseCase;
import com.poc.gateway.domain.port.outbound.PaymentWriteRepository;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentResponseDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class UpdatePaymentStatusService implements UpdatePaymentStatusUseCase {

    @Inject
    PaymentWriteRepository paymentRepo;

    @Inject
    PaymentMapper mapper;

    @Override
    @Transactional
    public PaymentResponseDTO execute(String paymentId, String newStatus) {
        if (paymentId == null) {
            throw new PaymentNotFoundException("null");
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            throw new PaymentNotFoundException(paymentId);
        }

        PaymentStatus status = PaymentStatus.fromString(newStatus)
            .orElseThrow(() -> new IllegalArgumentException("Invalid payment status: " + newStatus));

        Payment updated = paymentRepo.update(uuid, status);
        return mapper.toResponseDTO(updated);
    }
}
