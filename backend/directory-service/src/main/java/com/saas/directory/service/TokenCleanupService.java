package com.saas.directory.service;

import com.saas.directory.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TokenCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenCleanupService.class);

    private final TokenRepository tokenRepository;

    public TokenCleanupService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(initialDelay = 600_000L, fixedDelay = 3_600_000L)
    @Transactional
    public int purgeExpiredTokens() {
        final int deleted = tokenRepository.deleteAllExpiredBefore(Instant.now());
        if (deleted > 0) {
            LOGGER.info("Purged [{}] expired authentication tokens", deleted);
        }
        return deleted;
    }
}
