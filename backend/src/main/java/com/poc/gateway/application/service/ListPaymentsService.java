package com.poc.gateway.application.service;

import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.inbound.ListPaymentsUseCase;
import com.poc.gateway.domain.port.outbound.PaymentReadRepository;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.shared.dto.PaymentPageResponseDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ListPaymentsService implements ListPaymentsUseCase {

    @Inject
    PaymentReadRepository paymentRepo;

    @Inject
    PaymentMapper mapper;

    @Override
    public PaymentPageResponseDTO execute(String customerId, String status, int limit, int offset) {
        PaymentStatus paymentStatus = PaymentStatus.fromString(status).orElse(null);

        List<PaymentResponseDTO> payments = paymentRepo.findAll(customerId, paymentStatus, limit, offset)
            .stream()
            .map(mapper::toResponseDTO)
            .toList();

        long total = paymentRepo.count(customerId, paymentStatus);

        return new PaymentPageResponseDTO(payments, total, limit, offset);
    }
}
