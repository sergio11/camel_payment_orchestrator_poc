package com.poc.gateway.application.mapper;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.shared.dto.PaymentMetadataDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface PaymentMetadataApplicationMapper {

    PaymentMetadataDTO toDTO(PaymentMetadata source);

    PaymentMetadata toDomain(PaymentMetadataDTO dto);
}
