package com.saas.directory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileUpdateDto(
        @NotNull @Valid ProfileDto profile,
        @NotBlank @Size(max = 256) String currentPassword) {
    @Override
    public String toString() {
        return "ProfileUpdateDto[redacted]";
    }
}
