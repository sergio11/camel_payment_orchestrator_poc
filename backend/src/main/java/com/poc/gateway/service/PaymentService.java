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
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentService {
    
    private static final Logger LOG = Logger.getLogger(PaymentService.class);
    
    @Inject
    PaymentRepository repository;
    
    @Inject
    KafkaEventPublisher kafkaEventPublisher;
    
    public PaymentResponse createPayment(PaymentRequest request) {
        Payment payment = PaymentMapper.toEntity(request);
        Payment saved = repository.save(payment);
        
        kafkaEventPublisher.publishPaymentReceived(
            saved.id().toString(),
            saved.amount(),
            saved.currency(),
            saved.customerId(),
            saved.paymentMethod(),
            saved.country(),
            saved.metadata()
        );
        
        return PaymentMapper.toResponse(saved);
    }
    
    public PaymentResponse getPayment(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new PaymentNotFoundException(id);
        }
        Payment payment = repository.findById(uuid)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(payment);
    }
    
    public List<PaymentResponse> listPayments(String customerId, String status, int limit, int offset) {
        PaymentStatus paymentStatus = null;
        if (status != null) {
            try {
                paymentStatus = PaymentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                LOG.warnf("Invalid payment status: %s, returning all payments", status);
            }
        }
        return repository.findAll(customerId, paymentStatus, limit, offset)
            .stream()
            .map(PaymentMapper::toResponse)
            .toList();
    }

    public long countPayments(String customerId, String status) {
        PaymentStatus paymentStatus = null;
        if (status != null) {
            try {
                paymentStatus = PaymentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                LOG.warnf("Invalid payment status: %s, counting all payments", status);
            }
        }
        return repository.count(customerId, paymentStatus);
    }
}