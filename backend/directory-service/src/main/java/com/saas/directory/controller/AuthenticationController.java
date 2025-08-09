package com.saas.directory.controller;


import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.helper.TargetUser;
import com.saas.directory.service.AuthenticationManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.saas.directory.model.TokenType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AuthenticationController {
    public static Logger LOGGER = LoggerFactory.getLogger(AuthenticationController.class);

    private final AuthenticationManager authenticationManager;
    private final UserAuthenticationProvider userAuthenticationProvider;

    public AuthenticationController(AuthenticationManager authenticationManager, UserAuthenticationProvider userAuthenticationProvider) {
        this.authenticationManager = authenticationManager;
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<UserAuthDto> authenticateUser(@RequestBody CredentialsDto credentialsDto) {
        LOGGER.info("User [{}] is logging-in", credentialsDto.username());

        final UserAuthDto userAuthDto = authenticationManager.login(credentialsDto);
        return ResponseEntity.ok(userAuthDto);
    }

    @PostMapping("/refresh-token")
    public void refreshToken(@TargetUser String username, HttpServletRequest request, HttpServletResponse response) throws IOException, IllegalAccessException {
        LOGGER.info("User [{}] is refreshing is access token", username);

        userAuthenticationProvider.refreshToken(username, request, response);
    }

    @PostMapping("/signout")
    public ResponseEntity<Boolean> logout(@TargetUser String username, HttpServletRequest request) {
        LOGGER.info("User [{}] is logging out", username);

        authenticationManager.logout(request);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/validate-token")
    public ResponseEntity<Authentication> validateToken(HttpServletRequest request) throws IllegalAccessException {
        final String token = userAuthenticationProvider.extractToken(request);
        final Authentication authentication = userAuthenticationProvider.authenticateWithToken(token, TokenType.AUTH_ACCESS);
        return ResponseEntity.ok(authentication);
    }
}
