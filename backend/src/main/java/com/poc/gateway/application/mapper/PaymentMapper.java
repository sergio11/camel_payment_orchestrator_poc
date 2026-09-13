package com.poc.gateway.application.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.CreatePaymentCommand;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import jakarta.inject.Inject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public abstract class PaymentMapper {

    @Inject
    PaymentMetadataApplicationMapper metadataMapper;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Payment toDomain(PaymentRequestDTO request);

    public abstract CreatePaymentCommand toCommand(PaymentRequestDTO request);

    public PaymentResponseDTO toResponseDTO(Payment payment) {
        if (payment == null) {
            return null;
        }
        PaymentMetadataDTO metadataDto = metadataMapper.toDTO(payment.metadata());
        return new PaymentResponseDTO(
            payment.id() != null ? payment.id().toString() : null,
            payment.amount(),
            payment.currency(),
            payment.customerId(),
            payment.paymentMethod(),
            payment.country(),
            payment.status() != null ? payment.status().name() : null,
            payment.provider(),
            payment.failureReason(),
            metadataDto,
            payment.createdAt(),
            payment.updatedAt()
        );
    }
}
