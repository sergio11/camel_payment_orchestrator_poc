package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.port.inbound.UpdatePaymentStatusUseCase;
import com.poc.gateway.domain.port.outbound.PaymentWriteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class UpdatePaymentStatusService implements UpdatePaymentStatusUseCase {

    @Inject
    private PaymentWriteRepository paymentRepo;

    @Override
    @Transactional
    public Payment execute(UUID paymentId, PaymentStatus newStatus) {
        if (paymentId == null) {
            throw new PaymentNotFoundException("null");
        }
        Payment updated = paymentRepo.update(paymentId, newStatus);
        return updated;
    }
}
