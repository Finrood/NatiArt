package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Constructor-agnostic on purpose: the provider's constructor signature changes across
 * the stacked security PRs (e.g. ExternalUserRepository is added later), so the tests
 * instantiate the class reflectively (null dependencies are fine, the constructor only
 * assigns fields) and inject the secret via reflection.
 */
class UserAuthenticationProviderTest {

    private UserAuthenticationProvider providerWithSecret(String secret) throws ReflectiveOperationException {
        final Constructor<?> ctor = UserAuthenticationProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        final UserAuthenticationProvider provider =
                (UserAuthenticationProvider) ctor.newInstance(new Object[ctor.getParameterCount()]);
        ReflectionTestUtils.setField(provider, "secretKey", secret);
        return provider;
    }

    @Test
    void initFailsFastOnBlankSecretInsteadOfSilentlySigningWithEmptyKey() throws ReflectiveOperationException {
        assertThrows(IllegalStateException.class, () -> providerWithSecret("").init());
        assertThrows(
                IllegalStateException.class, () -> providerWithSecret("   ").init());
        assertThrows(IllegalStateException.class, () -> providerWithSecret(null).init());
    }

    @Test
    void initEncodesNonBlankSecret() throws ReflectiveOperationException {
        final String encoded = "real-secret-from-environment";
        assertDoesNotThrow(() -> providerWithSecret(encoded).init());
    }
}
