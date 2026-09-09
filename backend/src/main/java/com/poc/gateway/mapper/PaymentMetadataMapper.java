package com.poc.gateway.mapper;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentMetadataEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface PaymentMetadataMapper {

    PaymentMetadataEntity toEntity(PaymentMetadata source);

    PaymentMetadata toDomain(PaymentMetadataEntity entity);
}
