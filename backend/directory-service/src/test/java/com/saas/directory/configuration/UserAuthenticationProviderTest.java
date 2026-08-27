package com.saas.directory.configuration;

import com.saas.directory.repository.TokenRepository;
import com.saas.directory.service.UserManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class UserAuthenticationProviderTest {

    private UserAuthenticationProvider providerWithSecret(String secret) {
        final UserAuthenticationProvider provider =
                new UserAuthenticationProvider(mock(TokenRepository.class), mock(UserManager.class));
        ReflectionTestUtils.setField(provider, "secretKey", secret);
        return provider;
    }

    @Test
    void initFailsFastOnBlankSecretInsteadOfSilentlySigningWithEmptyKey() {
        assertThrows(IllegalStateException.class, () -> providerWithSecret("").init());
        assertThrows(IllegalStateException.class, () -> providerWithSecret("   ").init());
        assertThrows(IllegalStateException.class, () -> providerWithSecret(null).init());
    }

    @Test
    void initEncodesNonBlankSecret() {
        final String encoded = "real-secret-from-environment";
        assertDoesNotThrow(() -> providerWithSecret(encoded).init());
    }
}
