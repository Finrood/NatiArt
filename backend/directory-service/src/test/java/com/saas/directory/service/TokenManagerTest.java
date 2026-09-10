package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.TokenRepository;

@ExtendWith(MockitoExtension.class)
public class TokenManagerTest {
    @Mock
    private TokenRepository tokenRepository;

    @InjectMocks
    private TokenManager tokenManager;

    @Test
    public void getTokenByJtiAndTokenTypeOrDie_unknownJti_throwsStaticMessageWithoutJti() {
        final String jti = "presented-jti-123";
        when(tokenRepository.findByJtiAndTokenType(jti, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        final IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tokenManager.getTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET));

        assertEquals("Invalid token", thrown.getMessage());
        assertFalse(thrown.getMessage().contains(jti));
    }

    @Test
    public void getTokenByJtiAndTokenTypeOrDie_knownJti_returnsToken() {
        final String jti = "known-jti";
        final User user = new User("user", "oldpass");
        final Token token =
                new Token(jti, user, TokenType.PASSWORD_RESET, Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByJtiAndTokenType(jti, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertEquals(token, tokenManager.getTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET));
    }

    @Test
    public void getValidTokenByJtiAndTokenTypeOrDie_unknownJti_throwsStaticMessageWithoutJti() {
        final String jti = "presented-jti-456";
        when(tokenRepository.findByJtiAndTokenType(jti, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        final IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tokenManager.getValidTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET));

        assertEquals("Invalid token", thrown.getMessage());
        assertFalse(thrown.getMessage().contains(jti));
    }

    @Test
    public void getValidTokenByJtiAndTokenTypeOrDie_expiredToken_throwsStaticMessageWithoutJti() {
        final String jti = "expired-jti-789";
        final User user = new User("user", "oldpass");
        final Token token =
                new Token(jti, user, TokenType.PASSWORD_RESET, Instant.now().minus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByJtiAndTokenType(jti, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        final IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tokenManager.getValidTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET));

        assertEquals("Invalid token", thrown.getMessage());
        assertFalse(thrown.getMessage().contains(jti));
    }

    @Test
    public void getValidTokenByJtiAndTokenTypeOrDie_validToken_returnsToken() {
        final String jti = "valid-jti";
        final User user = new User("user", "oldpass");
        final Token token =
                new Token(jti, user, TokenType.PASSWORD_RESET, Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByJtiAndTokenType(jti, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertEquals(token, tokenManager.getValidTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET));
    }
}
