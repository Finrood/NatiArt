package com.saas.directory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.saas.directory.service.PasswordPolicy;

public record UserRegistrationDto(
        @NotBlank @Email @Size(max = 255) String username,

        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.PASSWORD_PATTERN)
        String password,

        @NotNull @Valid ProfileDto profile) {}
