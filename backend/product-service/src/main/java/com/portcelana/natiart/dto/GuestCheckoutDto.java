package com.portcelana.natiart.dto;

import java.time.Instant;

public record GuestCheckoutDto(
        String id,
        String customerId,
        String email,
        Object profile,
        String csrfToken,
        String externalId,
        String provisioningStatus,
        Instant expiresAt,
        boolean remembered,
        String attemptJson) {}
