package com.poc.gateway.mapper;

import java.lang.reflect.Field;
import org.mapstruct.factory.Mappers;

public final class MapperTestHelper {

    private MapperTestHelper() {
    }

    public static PaymentPersistenceMapper persistenceMapper() {
        PaymentPersistenceMapper impl = Mappers.getMapper(PaymentPersistenceMapper.class);
        PaymentMetadataMapper metaMapper = Mappers.getMapper(PaymentMetadataMapper.class);
        try {
            Field f = PaymentPersistenceMapper.class.getDeclaredField("metadataMapper");
            f.setAccessible(true);
            f.set(impl, metaMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return impl;
    }
}
