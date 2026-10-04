package com.saas.directory.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saas.directory.dto.UserDto;
import com.saas.directory.helper.TargetUser;
import com.saas.directory.model.AsaasProvisioningStatus;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import com.saas.directory.service.AsaasProvisioningStateService;
import com.saas.directory.service.UserManager;

@RestController
public class UserController {
    public static Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    private final UserManager userManager;
    private final AsaasProvisioningStateService provisioningState;

    public UserController(UserManager userManager, AsaasProvisioningStateService provisioningState) {
        this.userManager = userManager;
        this.provisioningState = provisioningState;
    }

    @GetMapping("/users/current")
    public ResponseEntity<UserDto> currentUser(@TargetUser String username) {
        LOGGER.debug("User [{}] is getting current logged-in user", username);

        if (username == null || username.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        final User user = userManager.getUserOrDie(username);
        final UserDto userDto = UserDto.from(user, null);
        userDto.setExternalId(userManager
                .getAsaasCustomer(username)
                .map(ExternalUser::getExternalId)
                .orElse(null));
        provisioningState
                .statusForUser(user.getId())
                .ifPresent(status -> userDto.setProvisioningStatus(status.status())
                        .setProvisioningNextAttemptAt(status.nextAttemptAt()));
        if (userDto.getExternalId() != null) {
            userDto.setProvisioningStatus(AsaasProvisioningStatus.SUCCEEDED);
        }
        return ResponseEntity.ok(userDto);
    }
}
