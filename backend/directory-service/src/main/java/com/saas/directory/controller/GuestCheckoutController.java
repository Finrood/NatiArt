package com.saas.directory.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.saas.directory.dto.*;
import com.saas.directory.service.*;

@RestController
public class GuestCheckoutController {
    public static final String COOKIE = "natiart-guest";
    public static final String SECURE_COOKIE = "__Host-natiart-guest";
    private final GuestCheckoutManager guests;
    private final AsaasProvisioningService provisioning;
    private final boolean secure;
    private final String internalSecret;

    public GuestCheckoutController(
            GuestCheckoutManager guests,
            AsaasProvisioningService provisioning,
            Environment environment,
            @Value("${natiart.auth-cache.invalidation-secret:}") String internalSecret,
            @Value("${natiart.guest.secure-cookie:#{null}}") Boolean secureCookie) {
        this.guests = guests;
        this.provisioning = provisioning;
        this.secure = secureCookie != null ? secureCookie : !environment.matchesProfiles("local-h2", "test");
        this.internalSecret = internalSecret;
    }

    @PostMapping("/guest/session")
    public GuestSessionDto start(
            @RequestHeader("X-Guest-Request") String marker,
            @CookieValue(value = COOKIE, required = false) String local,
            @CookieValue(value = SECURE_COOKIE, required = false) String tls,
            HttpServletResponse response) {
        if (!"1".equals(marker))
            throw new org.springframework.security.access.AccessDeniedException("Invalid guest request");
        final String previous = secure ? tls : local;
        if (previous != null) {
            try {
                return guests.validateOrDie(previous, null, false);
            } catch (org.springframework.security.access.AccessDeniedException expired) {
                /* replace an expired capability */
            }
        }
        final String token = GuestSecrets.randomToken();
        final GuestSessionDto session = guests.create(token);
        cookie(response, token, session);
        return session;
    }

    @GetMapping("/guest/session")
    public GuestSessionDto current(
            @CookieValue(value = COOKIE, required = false) String local,
            @CookieValue(value = SECURE_COOKIE, required = false) String tls) {
        return guests.validateOrDie(secure ? tls : local, null, false);
    }

    @PostMapping("/guest/session/details")
    public GuestSessionDto details(
            @CookieValue(value = COOKIE, required = false) String local,
            @CookieValue(value = SECURE_COOKIE, required = false) String tls,
            @RequestHeader("X-Guest-CSRF") String csrf,
            @Valid @RequestBody GuestDetailsDto details,
            HttpServletResponse response) {
        final String secret = secure ? tls : local;
        final GuestSessionDto initial = guests.saveDetails(secret, csrf, details);
        if (initial.externalId() == null) provisioning.provisionGuest(initial.customerId());
        final GuestSessionDto session = guests.validateOrDie(secret, null, false);
        cookie(response, secret, session);
        return session;
    }

    public record AttemptDto(String attemptJson, String expectedOrderId, String expectedAttemptKey) {}

    @PostMapping("/guest/session/attempt")
    public ResponseEntity<Void> attempt(
            @CookieValue(value = COOKIE, required = false) String local,
            @CookieValue(value = SECURE_COOKIE, required = false) String tls,
            @RequestHeader("X-Guest-CSRF") String csrf,
            @RequestBody AttemptDto attempt) {
        guests.saveAttempt(
                secure ? tls : local,
                csrf,
                attempt.attemptJson(),
                attempt.expectedOrderId(),
                attempt.expectedAttemptKey());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/guest/session")
    public ResponseEntity<Void> forget(
            @CookieValue(value = COOKIE, required = false) String local,
            @CookieValue(value = SECURE_COOKIE, required = false) String tls,
            @RequestHeader("X-Guest-CSRF") String csrf,
            HttpServletResponse response) {
        guests.forget(secure ? tls : local, csrf);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(secure ? SECURE_COOKIE : COOKIE, "")
                        .path("/")
                        .secure(secure)
                        .httpOnly(true)
                        .sameSite("Lax")
                        .maxAge(0)
                        .build()
                        .toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/guest/validate")
    public GuestSessionDto validate(
            @RequestHeader(value = "X-NatiArt-Internal-Secret", required = false) String supplied,
            @RequestBody GuestValidationDto request) {
        if (internalSecret == null || internalSecret.isBlank() || !GuestSecrets.equal(internalSecret, supplied))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return guests.validateOrDie(request.token(), request.csrfToken(), request.write());
    }

    private void cookie(HttpServletResponse response, String token, GuestSessionDto session) {
        final ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(secure ? SECURE_COOKIE : COOKIE, token)
                .path("/")
                .secure(secure)
                .httpOnly(true)
                .sameSite("Lax");
        if (session.remembered()) cookie.maxAge(30L * 86400);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
