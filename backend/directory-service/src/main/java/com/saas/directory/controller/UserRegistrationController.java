package com.saas.directory.controller;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.service.UserManager;

@RestController
public class UserRegistrationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegistrationController.class);

    private final UserManager userManager;

    public UserRegistrationController(UserManager userManager) {
        this.userManager = userManager;
    }

    @PostMapping("/register-user")
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody UserRegistrationDto userRegistrationDto)
            throws Exception {
        LOGGER.info("User [{}] is signing up", userRegistrationDto.username());

        final UserDto userDto = UserDto.from(userManager.registerUser(userRegistrationDto), null);
        return ResponseEntity.ok(userDto);
    }
}
