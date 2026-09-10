package com.poc.gateway.infrastructure.persistence.mapper;

import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.infrastructure.persistence.entity.OutboxEventEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface OutboxEventMapper {
    OutboxEventEntity toEntity(OutboxEvent domain);
    OutboxEvent toDomain(OutboxEventEntity entity);
}
