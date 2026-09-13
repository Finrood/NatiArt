package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.saas.directory.dto.ResetPasswordDto;
import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class PasswordManagerTest {
    @Mock
    private UserManager userManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimitStore rateLimitStore;

    @Mock
    private PasswordResetNotificationSender notificationSender;

    @InjectMocks
    private PasswordManager passwordManager;

    @Test
    public void doResetPassword_withExpiredToken_rejectsReset() {
        final String jti = "expired-jti";
        when(tokenManager.getValidTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET))
                .thenThrow(new IllegalArgumentException("Token [" + jti + "] is invalid"));

        assertThrows(
                IllegalArgumentException.class,
                () -> passwordManager.doResetPassword(jti, new ResetPasswordDto("newPass123", "newPass123")));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verify(tokenManager, never()).clearTokensOfUser(any());
    }

    @Test
    public void doResetPassword_withValidToken_resetsPasswordAndClearsTokens() {
        final String jti = "valid-jti";
        final User user = new User("user", "oldpass");
        final Token token =
                new Token(jti, user, TokenType.PASSWORD_RESET, Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenManager.getValidTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET))
                .thenReturn(token);
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded");
        when(tokenManager.consumeValidToken(jti, TokenType.PASSWORD_RESET)).thenReturn(true);

        passwordManager.doResetPassword(jti, new ResetPasswordDto("newPass123", "newPass123"));

        assertEquals("encoded", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(tokenManager).clearTokensOfUser(user);
        verify(tokenManager, never()).getTokenByJtiAndTokenTypeOrDie(any(), any());
    }

    @Test
    public void doResetPassword_withMismatchedPasswords_rejectsWithoutTouchingToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> passwordManager.doResetPassword("any-jti", new ResetPasswordDto("one", "two")));

        verify(tokenManager, never()).getValidTokenByJtiAndTokenTypeOrDie(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void doResetPassword_withBlankPassword_rejectsWithoutTouchingToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> passwordManager.doResetPassword("any-jti", new ResetPasswordDto("   ", "   ")));

        verify(tokenManager, never()).getValidTokenByJtiAndTokenTypeOrDie(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void notifyResetPassword_deliversAFragmentLinkForAnExistingAccount() {
        final User user = new User("customer@example.com", "OldPass1");
        final Token token = new Token(
                "reset-jti", user, TokenType.PASSWORD_RESET, Instant.now().plus(15, ChronoUnit.MINUTES));
        when(rateLimitStore.tryAcquire(any(), org.mockito.ArgumentMatchers.eq(3))).thenReturn(true);
        when(userManager.getUser("customer@example.com")).thenReturn(Optional.of(user));
        when(tokenManager.generateRandomUUIDToken(user, 15, ChronoUnit.MINUTES, TokenType.PASSWORD_RESET))
                .thenReturn(token);

        passwordManager.notifyResetPassword(" CUSTOMER@EXAMPLE.COM ");

        verify(tokenManager).clearTokensOfUserByType(user, TokenType.PASSWORD_RESET);
        verify(notificationSender).send(new PasswordResetNotification(
                "customer@example.com", "http://localhost:4200/reset-password#token=reset-jti"));
    }

    @Test
    public void notifyResetPassword_doesNotRevealWhetherAnAccountExists() {
        when(rateLimitStore.tryAcquire(any(), org.mockito.ArgumentMatchers.eq(3))).thenReturn(true);
        when(userManager.getUser("unknown@example.com")).thenReturn(Optional.empty());

        passwordManager.notifyResetPassword("unknown@example.com");

        verify(notificationSender, never()).send(any());
        verify(tokenManager, never()).generateRandomUUIDToken(any(), any(Long.class), any(), any());
    }

    @Test
    public void notifyResetPassword_doesNotLookUpAnAccountWhenThrottled() {
        when(rateLimitStore.tryAcquire(any(), org.mockito.ArgumentMatchers.eq(3))).thenReturn(false);

        passwordManager.notifyResetPassword("customer@example.com");

        verify(userManager, never()).getUser(any());
        verify(notificationSender, never()).send(any());
    }

    @Test
    public void doResetPassword_rejectsAConcurrentSecondRedemption() {
        final User user = new User("customer@example.com", "OldPass1");
        final Token token = new Token(
                "reset-jti", user, TokenType.PASSWORD_RESET, Instant.now().plus(15, ChronoUnit.MINUTES));
        when(tokenManager.getValidTokenByJtiAndTokenTypeOrDie("reset-jti", TokenType.PASSWORD_RESET))
                .thenReturn(token);
        when(tokenManager.consumeValidToken("reset-jti", TokenType.PASSWORD_RESET)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> passwordManager.doResetPassword("reset-jti", new ResetPasswordDto("NewPass1", "NewPass1")));

        verify(userRepository, never()).save(any());
        verify(tokenManager, never()).clearTokensOfUser(any());
    }
}
