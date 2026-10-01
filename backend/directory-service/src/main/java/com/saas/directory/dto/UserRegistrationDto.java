package com.saas.directory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRegistrationDto(
        @NotBlank @Email @Size(max = 255) String username,
        @NotBlank @Size(min = 8, max = 255) String password,
        @NotNull @Valid ProfileDto profile) {}
