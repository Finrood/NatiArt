package com.saas.directory.controller;

import com.saas.directory.dto.UserDto;
import com.saas.directory.helper.TargetUser;
import com.saas.directory.service.UserManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    private final UserManager userManager;

    public UserController(UserManager userManager) {
        this.userManager = userManager;
    }

    @GetMapping("/users/current")
    public ResponseEntity<UserDto> currentUser(@TargetUser String username) {
        if (username == null || username.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        final UserDto userDto = UserDto.from(userManager.getUserOrDie(username));
        userDto.setExternalId(userManager.getAsaasCustomerOrDie(username).getExternalId());
        return ResponseEntity.ok(userDto);
    }
}
