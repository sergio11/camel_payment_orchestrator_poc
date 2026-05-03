package com.poc.gateway.mapper;

import com.poc.gateway.dto.PaymentRequest;
import com.poc.gateway.dto.PaymentResponse;
import com.poc.gateway.entity.Payment;
import java.time.LocalDateTime;

public class PaymentMapper {
    
    public static Payment toEntity(PaymentRequest request) {
        return Payment.create(
            request.amount(),
            request.currency(),
            request.customerId(),
            request.paymentMethod(),
            request.country(),
            request.metadata()
        );
    }
    
    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.id().toString(),
            payment.amount(),
            payment.currency(),
            payment.customerId(),
            payment.paymentMethod(),
            payment.country(),
            payment.status() != null ? payment.status().name() : null,
            payment.provider(),
            payment.failureReason(),
            payment.metadata(),
            payment.createdAt(),
            payment.updatedAt()
        );
    }
}