package com.saas.directory.dto;

import java.time.Instant;

import com.saas.directory.model.AsaasProvisioningStatus;

public record GuestSessionDto(
        String id,
        String customerId,
        String email,
        ProfileDto profile,
        String csrfToken,
        String externalId,
        AsaasProvisioningStatus provisioningStatus,
        Instant expiresAt,
        boolean remembered,
        String attemptJson) {}
