package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.model.User;

@ExtendWith(MockitoExtension.class)
public class AuthenticationManagerTest {
    private final UserManager userManager = mock(UserManager.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserAuthenticationProvider userAuthenticationProvider = mock(UserAuthenticationProvider.class);
    private final TokenManager tokenManager = mock(TokenManager.class);

    private AuthenticationManager authenticationManager;

    @BeforeEach
    public void initContext() {
        authenticationManager =
                new AuthenticationManager(userManager, passwordEncoder, userAuthenticationProvider, tokenManager);
    }

    // Unknown users and wrong passwords must be indistinguishable
    @Test
    public void login_with_unknown_user_is_generic() {
        when(userManager.getUser("ghost@attacker.com")).thenReturn(java.util.Optional.empty());

        final ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
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

        final ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> authenticationManager.login(new CredentialsDto(username, null)));
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

    @Test
    public void logout_clears_every_token_of_the_user() {
        final String username = "testUser";
        final User user = new User(username, "testPassword");
        when(userManager.getUserOrDie(username)).thenReturn(user);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(); // ensure a session exists
        authenticationManager.logout(request, username);

        // Logout must invalidate the HTTP session and clear ALL tokens (access AND refresh)
        // for the user, not just the presented access token.
        assertNull(request.getSession(false));
        verify(tokenManager).clearTokensOfUser(user);
    }

    @Test
    public void logout_without_username_falls_back_to_presented_bearer_token() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        request.addHeader("Authorization", "Bearer some-jti");
        authenticationManager.logout(request, null);

        // Without a resolvable username the tokenManager is not involved; the bearer fallback
        // path only clears the presented token. No username -> no bulk token purge.
        verify(tokenManager, never()).clearTokensOfUser(any());
    }
}
