package com.portcelana.natiart.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.service.GuestOrderClaimManager;

@RestController
public class GuestOrderClaimController {
    private final GuestOrderClaimManager claims;
    private final String secret;

    public GuestOrderClaimController(
            GuestOrderClaimManager claims, @Value("${natiart.auth-cache.invalidation-secret:}") String secret) {
        this.claims = claims;
        this.secret = secret;
    }

    public record Claim(String claimId, String accountId, String email, Instant cutoff) {}

    @PostMapping("/internal/guest/claim-orders")
    public ResponseEntity<Void> claim(
            @RequestBody Claim claim,
            @RequestHeader(value = "X-NatiArt-Internal-Secret", required = false) String supplied) {
        if (secret == null || secret.isBlank())
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        if (supplied == null
                || !MessageDigest.isEqual(
                        secret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        claims.link(claim.claimId(), claim.accountId(), claim.email(), claim.cutoff());
        return ResponseEntity.noContent().build();
    }
}
