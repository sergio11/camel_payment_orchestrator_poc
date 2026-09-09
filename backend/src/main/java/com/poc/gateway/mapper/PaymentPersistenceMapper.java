package com.poc.gateway.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentEntity;
import jakarta.inject.Inject;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "cdi")
public abstract class PaymentPersistenceMapper {

    @Inject
    PaymentMetadataMapper metadataMapper;

    @Mapping(target = "id", expression = "java(payment.id() != null ? payment.id() : java.util.UUID.randomUUID())")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", expression = "java(payment.createdAt() != null ? payment.createdAt() : java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(payment.updatedAt() != null ? payment.updatedAt() : java.time.LocalDateTime.now())")
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "version", ignore = true)
    public abstract PaymentEntity toEntity(Payment payment);

    @AfterMapping
    void mapMetadata(Payment source, @MappingTarget PaymentEntity target) {
        target.metadata = metadataMapper.toEntity(source.metadata());
        if (target.metadata != null) {
            target.metadata.paymentId = target.id;
            target.metadata.payment = target;
        }
    }

    @Mapping(target = "metadata", expression = "java(metadataMapper.toDomain(entity.metadata))")
    public abstract Payment toDomain(PaymentEntity entity);
}
