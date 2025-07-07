package com.saas.directory.controller;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import com.saas.directory.repository.ExternalUserRepository;
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
    private final UserAuthenticationProvider userAuthenticationProvider;
    private final ExternalUserRepository externalUserRepository;

    public UserRegistrationController(UserManager userManager, UserAuthenticationProvider userAuthenticationProvider, ExternalUserRepository externalUserRepository) {
        this.userManager = userManager;
        this.userAuthenticationProvider = userAuthenticationProvider;
        this.externalUserRepository = externalUserRepository;
    }

    @PostMapping("/register-user")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserRegistrationDto userRegistrationDto) throws Exception {
        LOGGER.info("User [{}] is signing up", userRegistrationDto.username());

        final UserDto userDto = UserDto.from(userManager.registerUser(userRegistrationDto), null);
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/register-ghost-user")
    public ResponseEntity<UserAuthDto> registerGhostUser(@RequestBody UserRegistrationDto userRegistrationDto) throws Exception {
        LOGGER.info("Registering ghost user [{}]", userRegistrationDto.username());

        final User user = userManager.registerGhostUser(userRegistrationDto);
        ExternalUser externalUser = externalUserRepository.findByUser(user).orElse(null);
        final UserDto userDto = UserDto.from(user, externalUser);
        final String accessToken = userAuthenticationProvider.createAccessToken(userDto);
        final String refreshToken = userAuthenticationProvider.createRefreshToken(userDto);
        return ResponseEntity.ok(new UserAuthDto(accessToken, refreshToken));
    }
}
