package com.portcelana.natiart.configuration;

import java.util.Optional;

import com.portcelana.natiart.dto.AuthenticationResponseDto;

/** Shared durable cache contract for successful directory validations. */
public interface TokenValidationCache {
    Optional<AuthenticationResponseDto> get(String token);

    void put(String token, AuthenticationResponseDto response);
}
