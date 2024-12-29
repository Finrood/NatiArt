package com.saas.directory.controller;

import com.saas.directory.dto.UserDto;
import com.saas.directory.helper.TargetUser;
import com.saas.directory.service.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    public static Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    private final UserManager userManager;

    public UserController(UserManager userManager) {
        this.userManager = userManager;
    }

    @GetMapping("/users/current")
    public ResponseEntity<UserDto> currentUser(@TargetUser String username) {
        LOGGER.info("User [{}] is getting current logged-in user", username);

        if (username == null || username.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        final UserDto userDto = UserDto.from(userManager.getUserOrDie(username));
        userDto.setExternalId(userManager.getAsaasCustomerOrDie(username).getExternalId());
        return ResponseEntity.ok(userDto);
    }
}
