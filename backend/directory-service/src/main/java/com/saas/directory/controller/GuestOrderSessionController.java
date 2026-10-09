package com.saas.directory.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import com.saas.directory.service.*;

@RestController
public class GuestOrderSessionController {
    private final CheckoutClaimManager claims;
    private final GuestOrderSessionManager sessions;
    private final boolean secure;
    private final String secret;

    public GuestOrderSessionController(
            CheckoutClaimManager claims,
            GuestOrderSessionManager sessions,
            Environment environment,
            @Value("${natiart.auth-cache.invalidation-secret:}") String secret,
            @Value("${natiart.guest.secure-cookie:#{null}}") Boolean secureCookie) {
        this.claims = claims;
        this.sessions = sessions;
        this.secret = secret;
        secure = secureCookie != null ? secureCookie : !environment.matchesProfiles("local-h2", "test");
    }

    @PostMapping("/checkout-claim/track")
    public ResponseEntity<Void> track(
            @Valid @RequestBody CheckoutClaimController.Proof proof, HttpServletResponse response) {
        final String token = GuestSecrets.randomToken();
        claims.track(proof.token(), token);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(secure ? "__Host-natiart-guest-orders" : "natiart-guest-orders", token)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Lax")
                        .path("/")
                        .build()
                        .toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/guest/tracking/validate")
    public GuestOrderSessionManager.Scope validate(
            @RequestHeader(value = "X-NatiArt-Internal-Secret", required = false) String supplied,
            @RequestBody CheckoutClaimController.Proof proof) {
        if (secret.isBlank() || !GuestSecrets.equal(secret, supplied)) throw new AccessDeniedException("Access denied");
        return sessions.validateOrDie(proof.token());
    }
}
