package com.poc.gateway.service;

import com.poc.shared.dto.PaymentRequest;
import com.poc.shared.dto.PaymentResponse;
import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.exception.PaymentNotFoundException;
import com.poc.gateway.mapper.PaymentMapper;
import com.poc.gateway.repository.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentService {
    
    @Inject
    PaymentRepository repository;
    
    public PaymentResponse createPayment(PaymentRequest request) {
        Payment payment = PaymentMapper.toEntity(request);
        Payment saved = repository.save(payment);
        return PaymentMapper.toResponse(saved);
    }
    
    public PaymentResponse getPayment(String id) {
        UUID uuid = UUID.fromString(id);
        Payment payment = repository.findById(uuid)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(payment);
    }
    
    public List<PaymentResponse> listPayments(String customerId, String status, int limit, int offset) {
        PaymentStatus paymentStatus = status != null ? PaymentStatus.valueOf(status) : null;
        return repository.findAll(customerId, paymentStatus, limit, offset)
            .stream()
            .map(PaymentMapper::toResponse)
            .toList();
    }
}