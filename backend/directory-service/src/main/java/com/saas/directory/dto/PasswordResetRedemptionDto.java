package com.saas.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRedemptionDto(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 8, max = 255) String password,
        @NotBlank @Size(min = 8, max = 255) String passwordConfirmation) {}
