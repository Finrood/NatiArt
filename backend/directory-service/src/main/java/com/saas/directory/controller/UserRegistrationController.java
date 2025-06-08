package com.saas.directory.controller;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.service.AsaasUserManager;
import com.saas.directory.service.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserRegistrationController {
    public static Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    private final UserManager userManager;
    private final AsaasUserManager asaasUserManager;

    public UserRegistrationController(UserManager userManager, AsaasUserManager asaasUserManager) {
        this.userManager = userManager;
        this.asaasUserManager = asaasUserManager;
    }

    @PostMapping("/register-user")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserRegistrationDto userRegistrationDto) throws Exception {
        LOGGER.info("User [{}] is signing up", userRegistrationDto.username());

        final UserDto userDto = UserDto.from(userManager.registerUser(userRegistrationDto));
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/register-ghost-user")
    public ResponseEntity<UserDto> registerGhostUser(@RequestBody UserRegistrationDto userRegistrationDto) throws Exception {
        LOGGER.info("Registering ghost user [{}]", userRegistrationDto.username());

        final UserDto userDto = UserDto.from(userManager.registerGhostUser(userRegistrationDto));
        return ResponseEntity.ok(userDto);
    }
}
