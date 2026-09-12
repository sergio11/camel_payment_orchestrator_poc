package com.poc.gateway.application.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi", uses = PaymentMetadataApplicationMapper.class)
public interface PaymentMapper {

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    Payment toDomain(PaymentRequestDTO request);

    @Mapping(target = "id", expression = "java(payment.id() != null ? payment.id().toString() : null)")
    @Mapping(target = "status", expression = "java(payment.status() != null ? payment.status().name() : null)")
    PaymentResponseDTO toResponseDTO(Payment payment);
}