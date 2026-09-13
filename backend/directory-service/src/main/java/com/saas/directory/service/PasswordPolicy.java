package com.saas.directory.service;

import org.springframework.util.StringUtils;

/**
 * The password rules shared by registration and password recovery.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 255;
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
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password is too long");
        }
        if (!password.matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("Password must contain uppercase, lowercase, and numeric characters");
        }
    }
}
