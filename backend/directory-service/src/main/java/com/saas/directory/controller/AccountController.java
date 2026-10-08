package com.saas.directory.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.saas.directory.dto.*;
import com.saas.directory.helper.TargetUser;
import com.saas.directory.service.AccountManager;

@RestController
public class AccountController {
    private final AccountManager accounts;

    public AccountController(AccountManager accounts) {
        this.accounts = accounts;
    }

    @PutMapping("/users/current/profile")
    public ProfileDto updateProfile(@TargetUser String username, @Valid @RequestBody ProfileUpdateDto update) {
        return accounts.updateProfile(username, update);
    }

    @PostMapping("/users/current/change-password")
    public ResponseEntity<Void> changePassword(
            @TargetUser String username, @Valid @RequestBody PasswordChangeDto update) {
        accounts.changePassword(username, update);
        return ResponseEntity.noContent().build();
    }
}
