package com.poc.gateway.infrastructure.persistence.mapper;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.infrastructure.persistence.entity.PaymentMetadataEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface PaymentMetadataMapper {

    PaymentMetadataEntity toEntity(PaymentMetadata source);

    PaymentMetadata toDomain(PaymentMetadataEntity entity);
}
