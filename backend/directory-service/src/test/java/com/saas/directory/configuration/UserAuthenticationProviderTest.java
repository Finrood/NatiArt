package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.TokenRepository;
import com.saas.directory.service.UserManager;

/**
 * Constructor-agnostic on purpose: the provider's constructor signature changes across
 * the stacked security PRs (e.g. ExternalUserRepository is added later), so the tests
 * instantiate the class reflectively (null dependencies are fine, the constructor only
 * assigns fields) and inject the secret via reflection.
 */
@ExtendWith(MockitoExtension.class)
class UserAuthenticationProviderTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private ExternalUserRepository externalUserRepository;

    @Mock
    private UserManager userManager;

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

    @Test
    void authenticateWithToken_unknownJti_rejectsWithStaticMessageHidingTheToken() {
        final String secret = "disclosure-test-secret";
        final UserAuthenticationProvider provider = providerWithMocks(secret);
        final String jti = UUID.randomUUID().toString();
        final String token = signedToken(secret, jti, "alice");
        when(tokenRepository.findByJtiAndTokenType(eq(jti), eq(TokenType.AUTH_ACCESS)))
                .thenReturn(Optional.empty());

        final IllegalAccessException exception = assertThrows(
                IllegalAccessException.class,
                () -> provider.authenticateWithToken(token, TokenType.AUTH_ACCESS));

        assertEquals("Authentication token is not valid", exception.getMessage());
        assertFalse(exception.getMessage().contains(token));
    }

    @Test
    void authenticateWithToken_issuerMismatch_rejectsWithStaticMessageHidingTheToken() {
        final String secret = "disclosure-test-secret";
        final UserAuthenticationProvider provider = providerWithMocks(secret);
        final String jti = UUID.randomUUID().toString();
        final String token = signedToken(secret, jti, "alice");
        final User user = new User("bob", "s3cr3t-password");
        final Token dbToken =
                new Token(jti, user, TokenType.AUTH_ACCESS, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByJtiAndTokenType(eq(jti), eq(TokenType.AUTH_ACCESS)))
                .thenReturn(Optional.of(dbToken));

        final IllegalAccessException exception = assertThrows(
                IllegalAccessException.class,
                () -> provider.authenticateWithToken(token, TokenType.AUTH_ACCESS));

        assertEquals("Authentication token issuer mismatch", exception.getMessage());
        assertFalse(exception.getMessage().contains(token));
        verify(tokenRepository).deleteByJti(jti);
    }

    private UserAuthenticationProvider providerWithMocks(String secret) {
        final UserAuthenticationProvider provider =
                new UserAuthenticationProvider(tokenRepository, externalUserRepository, userManager);
        ReflectionTestUtils.setField(provider, "secretKey", secret);
        provider.init();
        return provider;
    }

    private String signedToken(String secret, String jti, String issuer) {
        final String encoded = Base64.getEncoder().encodeToString(secret.getBytes());
        return JWT.create()
                .withJWTId(jti)
                .withIssuer(issuer)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .withClaim("roles", "USER")
                .sign(Algorithm.HMAC256(encoded));
    }
}
