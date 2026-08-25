package com.saas.directory.service;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.service.TokenManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthenticationManagerTest {
    private final UserManager userManager = mock(UserManager.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserAuthenticationProvider userAuthenticationProvider = mock(UserAuthenticationProvider.class);
    private final TokenManager tokenManager = mock(TokenManager.class);

    private AuthenticationManager authenticationManager;

    @BeforeEach
    public void initContext() {
        authenticationManager = new AuthenticationManager(
                userManager,
                passwordEncoder,
                userAuthenticationProvider,
                tokenManager
        );
    }

    // Unknown users and wrong passwords must be indistinguishable
    @Test
    public void login_with_unknown_user_is_generic() {
        when(userManager.getUser("ghost@attacker.com")).thenReturn(java.util.Optional.empty());

        final ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> authenticationManager.login(new CredentialsDto("ghost@attacker.com", "whatever")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    public void login_with_wrong_password_is_generic_and_matches_unknown_user_error() {
        final String username = "testUser";
        final User user = new User(username, "testPassword");
        when(userManager.getUser(username)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(any(), eq(user.getPasswordHash()))).thenReturn(false);

        final ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> authenticationManager.login(new CredentialsDto(username, null)));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    public void successful_login_returns_tokens() {
        final String username = "testUser";
        final User user = new User(username, "testPassword")
                .setRole(new com.saas.directory.model.Role(com.saas.directory.model.RoleName.USER));
        when(userManager.getUser(username)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(eq("testPassword"), any())).thenReturn(true);
        when(userAuthenticationProvider.createAccessToken(any())).thenReturn("access");
        when(userAuthenticationProvider.createRefreshToken(any())).thenReturn("refresh");

        final UserAuthDto result = authenticationManager.login(new CredentialsDto(username, "testPassword"));

        assertEquals("access", result.getAccessToken());
        assertEquals("refresh", result.getRefreshToken());
    }
}