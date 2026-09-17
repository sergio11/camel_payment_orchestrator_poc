package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.inbound.GetPaymentUseCase;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GetPaymentService implements GetPaymentUseCase {

    @Inject
    private PaymentReadRepository paymentRepo;

    @Override
    public Payment execute(UUID paymentId) {
        if (paymentId == null) {
            throw new PaymentNotFoundException("null");
        }
        return paymentRepo.findById(paymentId)
            .orElseThrow(() -> PaymentNotFoundException.forId(paymentId.toString()));
    }

    @Override
    public Optional<Payment> executeByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return paymentRepo.findByIdempotencyKey(idempotencyKey.trim());
    }
}
