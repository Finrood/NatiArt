package com.saas.directory.service;

import java.nio.charset.StandardCharsets;

import org.springframework.util.StringUtils;

/**
 * The password rules shared by registration and password recovery.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$";

    private PasswordPolicy() {
        // Utility class.
    }

    public static void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        if (!password.matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("Password must contain uppercase, lowercase, and numeric characters");
        }
    }
}
