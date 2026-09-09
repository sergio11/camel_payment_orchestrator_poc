package com.poc.gateway.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.shared.dto.PaymentRequestDTO;
import com.poc.shared.dto.PaymentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "cdi")
public interface PaymentMapper {

    PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

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