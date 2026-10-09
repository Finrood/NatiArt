package com.saas.directory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record GuestDetailsDto(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull @Valid ProfileDto profile,
        boolean remember) {}
