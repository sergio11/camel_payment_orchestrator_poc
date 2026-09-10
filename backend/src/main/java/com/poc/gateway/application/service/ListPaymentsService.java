package com.poc.gateway.application.service;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentPageResult;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.inbound.ListPaymentsUseCase;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ListPaymentsService implements ListPaymentsUseCase {

    @Inject
    PaymentReadRepository paymentRepo;

    @Override
    public PaymentPageResult execute(String customerId, PaymentStatus status, int limit, int offset) {
        List<Payment> payments = paymentRepo.findAll(customerId, status, limit, offset);
        long total = paymentRepo.count(customerId, status);
        return new PaymentPageResult(payments, total, limit, offset);
    }
}
