package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saas.directory.repository.TokenRepository;

@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @Test
    void purgeDelegatesToRepositoryWithCurrentInstant() {
        when(tokenRepository.deleteAllExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(7);

        int deleted = new TokenCleanupService(tokenRepository).purgeExpiredTokens();

        assertEquals(7, deleted);
        verify(tokenRepository).deleteAllExpiredBefore(argThat(arg -> !((Instant) arg).isAfter(Instant.now())));
    }
}
