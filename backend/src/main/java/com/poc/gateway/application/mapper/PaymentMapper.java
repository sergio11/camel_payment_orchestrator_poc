package com.poc.gateway.application.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.CreatePaymentCommand;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi", uses = PaymentMetadataApplicationMapper.class)
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toDomain(PaymentRequestDTO request);

    CreatePaymentCommand toCommand(PaymentRequestDTO request);

    @Mapping(target = "id", expression = "java(payment.id() != null ? payment.id().toString() : null)")
    @Mapping(target = "status", expression = "java(payment.status() != null ? payment.status().name() : null)")
    PaymentResponseDTO toResponseDTO(Payment payment);
}
