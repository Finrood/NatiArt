package com.saas.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeDto(
        @NotBlank @Size(max = 256) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 8, max = 72) String passwordConfirmation) {
    @Override
    public String toString() {
        return "PasswordChangeDto[redacted]";
    }
}
