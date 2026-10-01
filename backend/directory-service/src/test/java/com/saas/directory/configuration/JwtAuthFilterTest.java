package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import com.saas.directory.model.TokenType;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.TokenRepository;
import com.saas.directory.service.UserManager;

/**
 * Locks the refresh-token routing contract of {@link JwtAuthFilter}: only an exact
 * {@code POST /refresh-token} validates the refresh token, and a failed authentication
 * short-circuits with 401 instead of falling through to the chain. Real JWTs
 * also lock the verifier-denial boundary against infrastructure failures.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private UserAuthenticationProvider userAuthenticationProvider;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private ExternalUserRepository externalUserRepository;

    @Mock
    private UserManager userManager;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exactRefreshTokenPost_validatesRefreshToken() throws Exception {
        assertEquals(TokenType.AUTH_REFRESH, capturedTokenTypeFor("POST", "/refresh-token"));
    }

    @Test
    void lookalikeRefreshTokenPath_validatesAccessToken() throws Exception {
        assertEquals(TokenType.AUTH_ACCESS, capturedTokenTypeFor("POST", "/api/refresh-token-evil"));
    }

    @Test
    void refreshTokenPathWithGetMethod_validatesAccessToken() throws Exception {
        assertEquals(TokenType.AUTH_ACCESS, capturedTokenTypeFor("GET", "/refresh-token"));
    }

    @Test
    void failedAuthentication_returns401WithoutContinuingTheChain() throws Exception {
        when(userAuthenticationProvider.authenticateWithToken(eq("test-token"), any(TokenType.class)))
                .thenThrow(new IllegalAccessException("Authentication token is not valid"));
        final JwtAuthFilter filter = new JwtAuthFilter(userAuthenticationProvider);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/users/current"), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals(
                ControllerAdvice.INVALID_TOKEN_MESSAGE,
                response.getContentAsString(),
                "the filter denial must carry the same static body as the advice handler");
        assertNull(chain.getRequest(), "the chain must NOT continue after a 401");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void verifierFailure_returns401WithoutContinuingTheChain() throws Exception {
        when(userAuthenticationProvider.authenticateWithToken(eq("test-token"), any(TokenType.class)))
                .thenThrow(new JWTVerificationException("expired"));
        final JwtAuthFilter filter = new JwtAuthFilter(userAuthenticationProvider);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/users/current"), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals(ControllerAdvice.INVALID_TOKEN_MESSAGE, response.getContentAsString());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void realVerifierExpiredBadSignatureAndMalformedTokensReturnStatic401() throws Exception {
        final String secret = "real-filter-verifier-secret";
        final String jti = UUID.randomUUID().toString();
        final String expired = signedToken(secret, jti, Instant.now().minus(1, ChronoUnit.HOURS));
        final String badSignature =
                signedToken("different-secret", jti, Instant.now().plus(10, ChronoUnit.MINUTES));
        final JwtAuthFilter filter = new JwtAuthFilter(realProvider(secret));

        for (final String token : List.of(expired, badSignature, "not.a.jwt")) {
            final MockHttpServletResponse response = new MockHttpServletResponse();
            final MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request("GET", "/users/current", token), response, chain);

            assertEquals(401, response.getStatus());
            assertEquals(ControllerAdvice.INVALID_TOKEN_MESSAGE, response.getContentAsString());
            assertFalse(response.getContentAsString().contains(token));
            assertNull(chain.getRequest(), "protected work must not run after verifier denial");
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        verifyNoInteractions(tokenRepository, externalUserRepository, userManager);
    }

    @Test
    void repositoryOutagePropagatesInsteadOfBecomingInvalidToken401() {
        final String secret = "repository-outage-test-secret";
        final String jti = UUID.randomUUID().toString();
        final String valid = signedToken(secret, jti, Instant.now().plus(10, ChronoUnit.MINUTES));
        final TransientDataAccessResourceException outage =
                new TransientDataAccessResourceException("synthetic database outage");
        when(tokenRepository.findByJtiAndTokenType(eq(jti), eq(TokenType.AUTH_ACCESS)))
                .thenThrow(outage);
        final JwtAuthFilter filter = new JwtAuthFilter(realProvider(secret));
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        assertThrows(
                TransientDataAccessResourceException.class,
                () -> filter.doFilter(request("GET", "/users/current", valid), response, chain));

        assertNotEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenRepository).findByJtiAndTokenType(jti, TokenType.AUTH_ACCESS);
    }

    @Test
    void validToken_continuesTheChain() throws Exception {
        when(userAuthenticationProvider.authenticateWithToken(eq("test-token"), any(TokenType.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        final JwtAuthFilter filter = new JwtAuthFilter(userAuthenticationProvider);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/users/current"), response, chain);

        assertNotNull(chain.getRequest());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private TokenType capturedTokenTypeFor(String method, String uri) throws Exception {
        when(userAuthenticationProvider.authenticateWithToken(eq("test-token"), any(TokenType.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        final JwtAuthFilter filter = new JwtAuthFilter(userAuthenticationProvider);

        filter.doFilter(request(method, uri), new MockHttpServletResponse(), new MockFilterChain());

        final ArgumentCaptor<TokenType> captor = ArgumentCaptor.forClass(TokenType.class);
        verify(userAuthenticationProvider).authenticateWithToken(eq("test-token"), captor.capture());
        return captor.getValue();
    }

    private UserAuthenticationProvider realProvider(String secret) {
        return new UserAuthenticationProvider(
                tokenRepository, externalUserRepository, userManager, secret, 3_600_000L, 604_800_000L);
    }

    private String signedToken(String secret, String jti, Instant expiresAt) {
        final String encoded = Base64.getEncoder().encodeToString(secret.getBytes());
        return JWT.create()
                .withJWTId(jti)
                .withIssuer("alice")
                .withIssuedAt(expiresAt.minus(10, ChronoUnit.MINUTES))
                .withExpiresAt(expiresAt)
                .sign(Algorithm.HMAC256(encoded));
    }

    private MockHttpServletRequest request(String method, String uri, String token) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private MockHttpServletRequest request(String method, String uri) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer test-token");
        return request;
    }
}
