package com.saas.directory.service;

import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class TokenManager {
    private final TokenRepository tokenRepository;

    public static boolean isTokenValid(Optional<Token> token) {
        return token
                .map(t -> !t.isExpired())
                .orElse(false);
    }

    public TokenManager(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Token> getTokenByTokenAndTokenType(String token, TokenType tokenType) {
        return tokenRepository.findByTokenAndTokenType(token, tokenType);
    }

    @Transactional(readOnly = true)
    public Token getTokenByTokenAndTokenTypeOrDie(String token, TokenType tokenType) {
        return tokenRepository.findByTokenAndTokenType(token, tokenType)
                .orElseThrow(() -> new IllegalArgumentException(String.format("[%s] Token [%s] does not exist", tokenType, token)));

    }

    @Transactional(readOnly = true)
    public Token getValidTokenTokenAndTokenTypeOrDie(String token, TokenType tokenType) {
        final Optional<Token> dbToken = tokenRepository.findByTokenAndTokenType(token, tokenType);

        if (! isTokenValid(dbToken)) {
            throw new IllegalArgumentException(String.format("Token [%s] is invalid", token));
        }
        return dbToken.get();
    }

    @Transactional
    public Token generateRandomUUIDToken(User user, long timeToLive, ChronoUnit timeUnit, TokenType tokenType) {
        final Instant expiryDate = Instant.now().plus(timeToLive, timeUnit);
        return tokenRepository.save(new Token(UUID.randomUUID().toString(), user, tokenType, expiryDate));
    }

    @Transactional
    public Token generateRandomSixNumbersToken(User user, long timeToLive, ChronoUnit timeUnit, TokenType tokenType) {
        final Instant expiryDate = Instant.now().plus(timeToLive, timeUnit);

        final Random random = new Random();

        final StringBuilder tokenStringBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int randomNumber = random.nextInt(10);
            tokenStringBuilder.append(randomNumber);
        }

        return tokenRepository.save(new Token(tokenStringBuilder.toString(), user, tokenType, expiryDate));
    }

    @Transactional
    public void clearTokensOfUser(User user) {
        tokenRepository.deleteAllByUser(user);
    }
}
