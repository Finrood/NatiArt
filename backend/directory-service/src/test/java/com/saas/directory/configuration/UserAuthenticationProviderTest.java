package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.TokenRepository;
import com.saas.directory.service.UserManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
                IllegalAccessException.class, () -> provider.authenticateWithToken(token, TokenType.AUTH_ACCESS));

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
                IllegalAccessException.class, () -> provider.authenticateWithToken(token, TokenType.AUTH_ACCESS));

        assertEquals("Authentication token issuer mismatch", exception.getMessage());
        assertFalse(exception.getMessage().contains(token));
        verify(tokenRepository).deleteByJti(jti);
    }

    @Test
    void invalidateToken_bogusToken_logsBelowError() throws ReflectiveOperationException {
        final UserAuthenticationProvider provider = providerWithSecret("bogus-token-test-secret");
        final Logger logger = (Logger) LoggerFactory.getLogger(UserAuthenticationProvider.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            assertDoesNotThrow(() -> provider.invalidateToken("not-a-jwt"));
            assertTrue(
                    appender.list.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR)));
            assertEquals(1, appender.list.size());
            assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void refreshToken_missingHeader_deniesInsteadOfAnsweringEmpty200() {
        final UserAuthenticationProvider provider = providerWithMocks("refresh-contract-secret");
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(eq(HttpHeaders.AUTHORIZATION))).thenReturn(null);

        assertThrows(IllegalAccessException.class, () -> provider.refreshToken("alice", request));
    }

    @Test
    void refreshToken_malformedHeader_deniesInsteadOfAnsweringEmpty200() {
        final UserAuthenticationProvider provider = providerWithMocks("refresh-contract-secret");
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(eq(HttpHeaders.AUTHORIZATION))).thenReturn("NotBearer");

        assertThrows(IllegalAccessException.class, () -> provider.refreshToken("alice", request));
    }

    @Test
    void refreshToken_usernameMismatch_denies() {
        final String secret = "refresh-contract-secret";
        final UserAuthenticationProvider provider = providerWithMocks(secret);
        final String jti = UUID.randomUUID().toString();
        final String refreshToken = signedToken(secret, jti, "alice");
        final User user = new User("alice", "s3cr3t-password").setRole(new Role(RoleName.USER));
        final Token dbToken =
                new Token(jti, user, TokenType.AUTH_REFRESH, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByJtiAndTokenType(eq(jti), eq(TokenType.AUTH_REFRESH)))
                .thenReturn(Optional.of(dbToken));
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(eq(HttpHeaders.AUTHORIZATION))).thenReturn("Bearer " + refreshToken);

        assertThrows(IllegalAccessException.class, () -> provider.refreshToken("mallory", request));
    }

    @Test
    void refreshToken_validBearer_returnsFreshAccessTokenPreservingTheRefreshToken() {
        final String secret = "refresh-contract-secret";
        final UserAuthenticationProvider provider = providerWithMocks(secret);
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", 600_000L);
        final String jti = UUID.randomUUID().toString();
        final String refreshToken = signedToken(secret, jti, "alice");
        final User user = new User("alice", "s3cr3t-password").setRole(new Role(RoleName.USER));
        final Token dbToken =
                new Token(jti, user, TokenType.AUTH_REFRESH, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByJtiAndTokenType(eq(jti), eq(TokenType.AUTH_REFRESH)))
                .thenReturn(Optional.of(dbToken));
        when(userManager.getUserOrDie(eq("alice"))).thenReturn(user);
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(eq(HttpHeaders.AUTHORIZATION))).thenReturn("Bearer " + refreshToken);

        final UserAuthDto result = assertDoesNotThrow(() -> provider.refreshToken("alice", request));

        assertEquals(refreshToken, result.getRefreshToken());
        assertTrue(result.getAccessToken() != null && !result.getAccessToken().isBlank());
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
