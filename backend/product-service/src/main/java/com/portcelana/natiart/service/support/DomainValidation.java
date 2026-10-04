package com.portcelana.natiart.service.support;

import java.math.BigDecimal;

/** Shared boundary checks for values persisted by the product service or sent to providers. */
public final class DomainValidation {
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");

    private DomainValidation() {}

    public static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InputValidationException(field, field + " must not be blank");
        }
        final String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InputValidationException(field, field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    public static void optionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InputValidationException(field, field + " must not exceed " + maxLength + " characters");
        }
    }

    public static void money(BigDecimal value, String field, boolean required) {
        if (value == null) {
            if (required) throw new InputValidationException(field, field + " must not be null");
            return;
        }
        if (value.signum() <= 0 || value.scale() > 2 || value.compareTo(MAX_MONEY) > 0) {
            throw new InputValidationException(field, field + " must be between 0.01 and 99999999.99");
        }
    }

    public static void orderTotal(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2 || value.compareTo(MAX_MONEY) > 0) {
            throw new InputValidationException("items", "Order total must be between 0.01 and 99999999.99");
        }
    }

    public static String cep(String value) {
        final String trimmed = value == null ? "" : value.strip();
        if (!trimmed.matches("[0-9]{5}-?[0-9]{3}")) {
            throw new InputValidationException("zipCode", "zipCode must contain exactly eight digits");
        }
        return trimmed.replace("-", "");
    }

    public static void finitePositive(float value, String field, float maximum) {
        if (!Float.isFinite(value) || value < 0.01f || value > maximum) {
            throw new InputValidationException(field, field + " must be finite and between 0.01 and " + maximum);
        }
    }
}
