package com.portcelana.natiart.service.support;

import java.math.BigDecimal;

/** Shared boundary checks for values persisted by the product service or sent to providers. */
public final class DomainValidation {
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");

    private DomainValidation() {}

    public static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }

    public static void optionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
    }

    public static void money(BigDecimal value, String field, boolean required) {
        if (value == null) {
            if (required) throw new IllegalArgumentException(field + " price must not be null");
            return;
        }
        if (value.signum() < 0 || value.scale() > 2 || value.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException(field + " price must be between 0.00 and 99999999.99");
        }
    }

    public static String cep(String value) {
        final String normalized = value == null ? "" : value.replaceAll("\\D", "");
        if (!normalized.matches("\\d{8}")) {
            throw new IllegalArgumentException("Destination postal code must contain exactly eight digits");
        }
        return normalized;
    }

    public static void finitePositive(float value, String field, float maximum) {
        if (!Float.isFinite(value) || value <= 0 || value > maximum) {
            throw new IllegalArgumentException(field + " must be finite and between 0 and " + maximum);
        }
    }
}
