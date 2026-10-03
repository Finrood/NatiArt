package com.saas.directory.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.saas.directory.dto.AccountStateChangeRequest;
import com.saas.directory.dto.AccountStateDto;
import com.saas.directory.service.AccountStateManager;

@RestController
public class AccountStateController {
    private final AccountStateManager accountStateManager;

    public AccountStateController(AccountStateManager accountStateManager) {
        this.accountStateManager = accountStateManager;
    }

    @PatchMapping("/admin/users/{userId}/account-state")
    public ResponseEntity<AccountStateDto> changeAccountState(
            @PathVariable String userId, @RequestBody AccountStateChangeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Account change is required");
        }
        return ResponseEntity.ok(
                AccountStateDto.from(accountStateManager.changeAccountState(userId, request.active(), request.role())));
    }
}
