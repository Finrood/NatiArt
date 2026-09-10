package com.saas.directory.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.dto.TokenValidationDto;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.helper.TargetUser;
import com.saas.directory.model.TokenType;
import com.saas.directory.service.AuthenticationManager;

@RestController
public class AuthenticationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationController.class);

    private final AuthenticationManager authenticationManager;
    private final UserAuthenticationProvider userAuthenticationProvider;

    public AuthenticationController(
            AuthenticationManager authenticationManager, UserAuthenticationProvider userAuthenticationProvider) {
        this.authenticationManager = authenticationManager;
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<UserAuthDto> authenticateUser(@Valid @RequestBody CredentialsDto credentialsDto) {
        LOGGER.info("User [{}] is logging-in", credentialsDto.username());

        final UserAuthDto userAuthDto = authenticationManager.login(credentialsDto);
        return ResponseEntity.ok(userAuthDto);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<UserAuthDto> refreshToken(@TargetUser String username, HttpServletRequest request)
            throws IllegalAccessException {
        LOGGER.info("User [{}] is refreshing is access token", username);

        final UserAuthDto userAuthDto = userAuthenticationProvider.refreshToken(username, request);
        return ResponseEntity.ok(userAuthDto);
    }

    @PostMapping("/signout")
    public ResponseEntity<Boolean> logout(@TargetUser String username, HttpServletRequest request) {
        LOGGER.info("User [{}] is logging out", username);

        authenticationManager.logout(request, username);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/validate-token")
    public ResponseEntity<TokenValidationDto> validateToken(HttpServletRequest request) throws IllegalAccessException {
        final String token = userAuthenticationProvider.extractToken(request);
        final Authentication authentication =
                userAuthenticationProvider.authenticateWithToken(token, TokenType.AUTH_ACCESS);
        return ResponseEntity.ok(TokenValidationDto.from(authentication));
    }
}
