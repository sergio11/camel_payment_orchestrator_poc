package com.poc.gateway.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record PaymentMetadata(
    String orderId,
    Integer attempts,
    Boolean isNewPaymentMethod,
    Integer paymentMethodAgeDays,
    String customerRiskTier,
    Map<String, Object> additionalProperties
) {
    public static PaymentMetadata empty() {
        return new PaymentMetadata(null, null, null, null, null, Map.of());
    }

    public static PaymentMetadata fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        Map<String, Object> additional = new HashMap<>(map);
        String orderId = (String) additional.remove("orderId");
        
        Integer attempts = null;
        Object attObj = additional.remove("attempts");
        if (attObj instanceof Number n) attempts = n.intValue();
        else if (attObj instanceof String s) {
            try { attempts = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }

        Boolean isNew = null;
        Object newObj = additional.remove("isNewPaymentMethod");
        if (newObj instanceof Boolean b) isNew = b;
        else if (newObj instanceof String s) isNew = Boolean.parseBoolean(s);

        Integer ageDays = null;
        Object ageObj = additional.remove("paymentMethodAgeDays");
        if (ageObj instanceof Number n) ageDays = n.intValue();
        else if (ageObj instanceof String s) {
            try { ageDays = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }

        String tier = (String) additional.remove("customerRiskTier");

        return new PaymentMetadata(orderId, attempts, isNew, ageDays, tier, Collections.unmodifiableMap(additional));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (orderId != null) map.put("orderId", orderId);
        if (attempts != null) map.put("attempts", attempts);
        if (isNewPaymentMethod != null) map.put("isNewPaymentMethod", isNewPaymentMethod);
        if (paymentMethodAgeDays != null) map.put("paymentMethodAgeDays", paymentMethodAgeDays);
        if (customerRiskTier != null) map.put("customerRiskTier", customerRiskTier);
        if (additionalProperties != null) map.putAll(additionalProperties);
        return Collections.unmodifiableMap(map);
    }
}
