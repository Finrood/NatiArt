package com.saas.directory.service;

import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TokenManagerTest {
    @Mock
    private TokenRepository tokenRepository;
    @Captor
    private ArgumentCaptor<Token> tokenCaptor;
    private TokenManager tokenManager;

    @BeforeEach
    void setUp() {
        tokenManager = new TokenManager(tokenRepository);
    }

    @Test
    void testGetTokenByTokenAndTokenType() {
        Token token = new Token("testToken", new User("user1", "password"), TokenType.AUTH_ACCESS, Instant.now());
        when(tokenRepository.findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS)).thenReturn(Optional.of(token));

        Optional<Token> result = tokenManager.getTokenByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS);

        assertTrue(result.isPresent());
        assertEquals(token, result.get());

        verify(tokenRepository).findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS);
    }

    @Test
    void testGetTokenByTokenAndTokenTypeOrDie() {
        Token token = new Token("testToken", new User("user1", "password"), TokenType.AUTH_ACCESS, Instant.now());
        when(tokenRepository.findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS)).thenReturn(Optional.of(token));

        Token result = tokenManager.getTokenByTokenAndTokenTypeOrDie("testToken", TokenType.AUTH_ACCESS);

        assertEquals(token, result);

        verify(tokenRepository).findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS);
    }

    @Test
    void testGetValidTokenTokenAndTokenTypeOrDie_ValidToken() {
        Token token = new Token("testToken", new User("user1", "password"), TokenType.AUTH_ACCESS, Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS)).thenReturn(Optional.of(token));

        Token result = tokenManager.getValidTokenTokenAndTokenTypeOrDie("testToken", TokenType.AUTH_ACCESS);

        assertEquals(token, result);

        verify(tokenRepository).findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS);
    }

    @Test
    void testGetValidTokenTokenAndTokenTypeOrDie_ExpiredToken() {
        Token token = new Token("testToken", new User("user1", "password"), TokenType.AUTH_ACCESS, Instant.now().minus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS)).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, () -> tokenManager.getValidTokenTokenAndTokenTypeOrDie("testToken", TokenType.AUTH_ACCESS));

        verify(tokenRepository).findByTokenAndTokenType("testToken", TokenType.AUTH_ACCESS);
    }

    @Test
    void testGenerateRandomUUIDToken() {
        User user = new User("user1", "password");
        when(tokenRepository.save(any(Token.class))).thenReturn(new Token("randomTokenId", user, TokenType.AUTH_ACCESS, Instant.now()));

        Token generatedToken = tokenManager.generateRandomUUIDToken(user, 1, ChronoUnit.HOURS, TokenType.AUTH_ACCESS);

        assertNotNull(generatedToken);
        assertEquals(user, generatedToken.getUser());
        assertEquals(TokenType.AUTH_ACCESS, generatedToken.getTokenType());

        // Verify that the save method was called with the correct parameters
        verify(tokenRepository).save(tokenCaptor.capture());
        Token capturedToken = tokenCaptor.getValue();
        assertEquals(user, capturedToken.getUser());
        assertEquals(TokenType.AUTH_ACCESS, capturedToken.getTokenType());
    }

    @Test
    void testGenerateRandomSixNumbersToken() {
        User user = new User("user1", "password");
        when(tokenRepository.save(any(Token.class))).thenReturn(new Token("123456", user, TokenType.AUTH_ACCESS, Instant.now()));

        Token generatedToken = tokenManager.generateRandomSixNumbersToken(user, 1, ChronoUnit.HOURS, TokenType.AUTH_ACCESS);

        assertNotNull(generatedToken);
        assertEquals(user, generatedToken.getUser());
        assertEquals(TokenType.AUTH_ACCESS, generatedToken.getTokenType());
        assertTrue(generatedToken.getToken().matches("\\d{6}")); // Check if token is 6 digits


        // Verify that the save method was called with the correct parameters
        verify(tokenRepository).save(tokenCaptor.capture());
        Token capturedToken = tokenCaptor.getValue();
        assertEquals(user, capturedToken.getUser());
        assertEquals(TokenType.AUTH_ACCESS, capturedToken.getTokenType());
    }

    @Test
    void testIsTokenValid_ValidToken() {
        Token validToken = new Token("testToken", new User("user1", "password"), TokenType.AUTH_ACCESS, Instant.now().plus(1, ChronoUnit.HOURS));

        assertTrue(TokenManager.isTokenValid(Optional.of(validToken)));
    }

    @Test
    void testIsTokenValid_ExpiredToken() {
        Token expiredToken = new Token("testToken", new User("user1", "password"), TokenType.AUTH_ACCESS, Instant.now().minus(1, ChronoUnit.HOURS));

        assertFalse(TokenManager.isTokenValid(Optional.of(expiredToken)));
    }

    @Test
    void testIsTokenValid_EmptyToken() {
        assertFalse(TokenManager.isTokenValid(Optional.empty()));
    }
}
