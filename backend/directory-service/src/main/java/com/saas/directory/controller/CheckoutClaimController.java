package com.saas.directory.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.saas.directory.service.CheckoutClaimManager;

@RestController
public class CheckoutClaimController {
    private final CheckoutClaimManager claims;

    public CheckoutClaimController(CheckoutClaimManager claims) {
        this.claims = claims;
    }

    public record Request(@NotBlank @Email @Size(max = 255) String email) {}

    public record Proof(@NotBlank @Size(max = 43) String token) {
        @Override
        public String toString() {
            return "Proof[redacted]";
        }
    }

    public record Confirmation(
            @NotBlank @Size(max = 43) String token,
            @NotBlank @Size(max = 72) String password,
            @Size(max = 72) String passwordConfirmation) {
        @Override
        public String toString() {
            return "Confirmation[redacted]";
        }
    }

    @PostMapping("/checkout-claim/request")
    public ResponseEntity<Void> request(@Valid @RequestBody Request request) {
        try {
            claims.request(request.email());
        } catch (org.springframework.mail.MailException failure) {
            /* delivery rollback retains the uniform public response */
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/checkout-claim/inspect")
    public CheckoutClaimManager.Inspection inspect(@Valid @RequestBody Proof proof) {
        return claims.inspect(proof.token());
    }

    @PostMapping("/checkout-claim/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody Confirmation request)
            throws javax.management.relation.RoleNotFoundException {
        claims.confirm(request.token(), request.password(), request.passwordConfirmation());
        return ResponseEntity.noContent().build();
    }
}
