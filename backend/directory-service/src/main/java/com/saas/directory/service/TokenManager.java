package com.saas.directory.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.TokenRepository;

@Service
public class TokenManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManager.class);

    private final TokenRepository tokenRepository;

    public TokenManager(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public static boolean isTokenValid(Optional<Token> token) {
        return token.map(t -> !t.isExpired()).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Token> getTokenByJtiAndTokenType(String jti, TokenType tokenType) {
        return tokenRepository.findByJtiAndTokenType(jti, tokenType);
    }

    @Transactional(readOnly = true)
    public Token getTokenByJtiAndTokenTypeOrDie(String jti, TokenType tokenType) {
        final Optional<Token> token = tokenRepository.findByJtiAndTokenType(jti, tokenType);
        if (token.isEmpty()) {
            LOGGER.debug("Rejected unknown [{}] token", tokenType);
            throw new IllegalArgumentException("Invalid token");
        }
        return token.get();
    }

    @Transactional(readOnly = true)
    public Token getValidTokenByJtiAndTokenTypeOrDie(String jti, TokenType tokenType) {
        final Optional<Token> dbToken = tokenRepository.findByJtiAndTokenType(jti, tokenType);

        if (!isTokenValid(dbToken)) {
            LOGGER.debug("Rejected invalid [{}] token", tokenType);
            throw new IllegalArgumentException("Invalid token");
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

        final StringBuilder jtiStringBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int randomNumber = random.nextInt(10);
            jtiStringBuilder.append(randomNumber);
        }

        return tokenRepository.save(new Token(jtiStringBuilder.toString(), user, tokenType, expiryDate));
    }

    @Transactional
    public void clearTokensOfUser(User user) {
        tokenRepository.deleteAllByUser(user);
    }
}
