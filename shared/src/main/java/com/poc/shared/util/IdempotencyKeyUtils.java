package com.poc.shared.util;

import java.util.UUID;

public final class IdempotencyKeyUtils {

    private IdempotencyKeyUtils() {}

    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return key.trim();
    }
}
