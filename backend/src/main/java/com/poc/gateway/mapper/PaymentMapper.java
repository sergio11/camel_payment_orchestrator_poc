package com.poc.gateway.mapper;

import com.poc.gateway.dto.PaymentRequest;
import com.poc.gateway.dto.PaymentResponse;
import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import java.util.UUID;

public class PaymentMapper {
    
    public static Payment toEntity(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerId(request.getCustomerId());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setCountry(request.getCountry());
        payment.setMetadata(request.getMetadata());
        return payment;
    }
    
    public static PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId().toString());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setCustomerId(payment.getCustomerId());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setCountry(payment.getCountry());
        response.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        response.setProvider(payment.getProvider());
        response.setFailureReason(payment.getFailureReason());
        response.setMetadata(payment.getMetadata());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}