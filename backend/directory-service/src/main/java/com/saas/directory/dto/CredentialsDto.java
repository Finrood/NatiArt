package com.saas.directory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredentialsDto(
        @NotBlank @Email @Size(max = 255) String username,
        @NotBlank @Size(max = 255) String password) {}
