package com.saas.directory.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.saas.directory.dto.PasswordResetRedemptionDto;
import com.saas.directory.dto.PasswordResetRequestDto;
import com.saas.directory.dto.PasswordResetResponse;
import com.saas.directory.dto.ResetPasswordDto;
import com.saas.directory.service.PasswordManager;

@RestController
public class PasswordResetController {
    private static final String REQUEST_MESSAGE =
            "If an account exists for that address, a password reset link has been sent.";

    private final PasswordManager passwordManager;

    public PasswordResetController(PasswordManager passwordManager) {
        this.passwordManager = passwordManager;
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetResponse> requestReset(@Valid @RequestBody PasswordResetRequestDto request) {
        passwordManager.notifyResetPassword(request.username());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PasswordResetResponse(REQUEST_MESSAGE));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRedemptionDto request) {
        passwordManager.doResetPassword(
                request.token(), new ResetPasswordDto(request.password(), request.passwordConfirmation()));
        return ResponseEntity.noContent().build();
    }
}
