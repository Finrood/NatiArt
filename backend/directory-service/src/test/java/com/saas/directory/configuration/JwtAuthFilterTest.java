package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.saas.directory.model.TokenType;

/**
 * Locks the refresh-token routing contract of {@link JwtAuthFilter}: only an exact
 * {@code POST /refresh-token} validates the refresh token, and a failed authentication
 * short-circuits with 401 instead of falling through to the chain.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private UserAuthenticationProvider userAuthenticationProvider;

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
        assertNull(chain.getRequest(), "the chain must NOT continue after a 401");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
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

    private MockHttpServletRequest request(String method, String uri) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer test-token");
        return request;
    }
}
