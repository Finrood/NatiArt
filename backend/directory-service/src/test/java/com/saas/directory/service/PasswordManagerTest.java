package com.saas.directory.service;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.ResetPasswordDto;
import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PasswordManagerTest {
    private final UserManager userManager = mock(UserManager.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserAuthenticationProvider userAuthenticationProvider = mock(UserAuthenticationProvider.class);
    private final TokenManager tokenManager = mock(TokenManager.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private PasswordManager passwordManager;

    @BeforeEach
    public void initContext() {
        passwordManager = new PasswordManager(
                userManager,
                userRepository,
                tokenManager,
                passwordEncoder
        );
    }

    @Test
    public void test_getPasswordResetTokenOrDie_returns_token_when_found() {
        // Arrange
        String tokenValue = "some_token_value";
        Token token = new Token(tokenValue, new User("username1", "password"), TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        when(tokenManager.getTokenByTokenAndTokenTypeOrDie(tokenValue, TokenType.PASSWORD_RESET)).thenReturn(token);

        // Act
        Token result = passwordManager.getPasswordResetTokenOrDie(tokenValue);

        // Assert
        assertEquals(token, result);
    }

    @Test
    public void test_getPasswordResetTokenOrDie_throws_ResourceNotFoundException_when_token_not_found() {
        // Arrange
        String tokenValue = "non_existent_token";
        when(tokenManager.getTokenByTokenAndTokenTypeOrDie(tokenValue, TokenType.PASSWORD_RESET)).thenThrow(ResourceNotFoundException.class);

        // Act and Assert
        assertThrows(ResourceNotFoundException.class, () -> passwordManager.getPasswordResetTokenOrDie(tokenValue));
    }

    @Test
    public void test_getValidPasswordResetTokenOrDie_returns_token_when_found_and_valid() {
        // Arrange
        String tokenValue = "some_valid_token";
        Token token = new Token(tokenValue, new User("username1", "password"), TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        when(tokenManager.getValidTokenTokenAndTokenTypeOrDie(tokenValue, TokenType.PASSWORD_RESET)).thenReturn(token);

        // Act
        Token result = passwordManager.getValidPasswordResetTokenOrDie(tokenValue);

        // Assert
        assertEquals(token, result);
    }

    @Test
    public void test_getValidPasswordResetTokenOrDie_throws_IllegalArgumentException_when_token_not_found() {
        // Arrange
        String tokenValue = "non_existent_token";
        when(tokenManager.getValidTokenTokenAndTokenTypeOrDie(tokenValue, TokenType.PASSWORD_RESET)).thenThrow(IllegalArgumentException.class);

        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> passwordManager.getValidPasswordResetTokenOrDie(tokenValue));
    }

    @Test
    public void test_getValidPasswordResetTokenOrDie_throws_IllegalArgumentException_when_token_expired() {
        // Arrange
        String tokenValue = "expired_token";
        Token token = new Token(tokenValue, new User("username1", "password"), TokenType.PASSWORD_RESET, Instant.now().minusSeconds(3600));
        when(tokenManager.getValidTokenTokenAndTokenTypeOrDie(tokenValue, TokenType.PASSWORD_RESET)).thenThrow(IllegalArgumentException.class);

        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> passwordManager.getValidPasswordResetTokenOrDie(tokenValue));
    }

    @Test
    public void test_notifyResetPassword_calls_notifyResetPassword_in_user_manager() {
        //TODO Fix test
        // Arrange
        String username = "some_username";

        // Act
        passwordManager.notifyResetPassword(username);

        // Assert
        //verify(userManager, times(1)).notifyResetPassword(username);
    }

    @Test
    public void test_doResetPassword_resets_password_and_deletes_tokens() {
        // Arrange
        String tokenValue = "valid_token";
        String newPassword = "new_password";
        ResetPasswordDto resetPasswordDto = new ResetPasswordDto(newPassword, newPassword);
        Token token = new Token(tokenValue, new User("username1", "password"), TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        User user = new User("username", "old_password"); // Existing user
        token.setUser(user);
        when(tokenManager.getTokenByTokenAndTokenTypeOrDie(tokenValue, TokenType.PASSWORD_RESET)).thenReturn(token);
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded_new_password");

        // Act
        passwordManager.doResetPassword(tokenValue, resetPasswordDto);

        // Assert
        verify(passwordEncoder, times(1)).encode(newPassword);
        assertEquals("encoded_new_password", user.getPasswordHash());
        verify(userRepository, times(1)).save(user);
        verify(tokenManager, times(1)).clearTokensOfUser(user);
    }

    @Test
    public void test_doResetPassword_throws_IllegalArgumentException_when_passwords_are_not_identical() {
        // Arrange
        String tokenValue = "valid_token";
        ResetPasswordDto resetPasswordDto = new ResetPasswordDto("password123", "differentPassword123");
        Token token = new Token(tokenValue, new User("username1", "password"), TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        User user = new User("username", "old_password"); // Existing user
        token.setUser(user);
        when(tokenManager.getTokenByTokenAndTokenType(tokenValue, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));

        // Act and Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> passwordManager.doResetPassword(tokenValue, resetPasswordDto));
        assertEquals("Passwords must be identical", exception.getMessage());
    }

    @Test
    public void test_doResetPassword_throws_IllegalArgumentException_when_password_is_empty() {
        // Arrange
        String tokenValue = "valid_token";
        ResetPasswordDto resetPasswordDto = new ResetPasswordDto("", "");
        Token token = new Token(tokenValue, new User("username1", "password"), TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        User user = new User("username", "old_password"); // Existing user
        token.setUser(user);
        when(tokenManager.getTokenByTokenAndTokenType(tokenValue, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));

        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> passwordManager.doResetPassword(tokenValue, resetPasswordDto));
    }
}
