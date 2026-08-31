package com.portcelana.natiart.configuration;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the token->SecurityContext contract of {@link JwtAuthFilter} against a real
 * local directory-service stub:
 * <ul>
 *   <li>a valid token must produce an <b>authenticated</b> authentication carrying the granted
 *       authorities ({@code ROLE_...}) and the {@link AuthenticationResponseDto.Principal};</li>
 *   <li>an invalid token must short-circuit the request (401) and not continue the chain;</li>
 *   <li>a request without a token must skip validation entirely.</li>
 * </ul>
 * The first case was previously broken: the filter used the two-arg
 * {@code UsernamePasswordAuthenticationToken(principal, authorities)} constructor, which treats
 * the second argument as <em>credentials</em> and yields an <b>unauthenticated</b> token with
 * empty authorities - so {@code @PreAuthorize("hasRole('ADMIN')")} and
 * {@code isFullyAuthenticated()} denied every legitimate (and admin) request.
 *
 * @see SecurityConfig
 */
class JwtAuthFilterTest {

    private static final String VALID_AUTH_JSON = """
            {
              "authorities": [{"authority": "ROLE_USER"}],
              "authenticated": true,
              "principal": {"id": "u1", "username": "jane", "role": "USER", "externalId": "cus_MINE"},
              "name": "jane"
            }
            """;

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        SecurityContextHolder.clearContext();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        SecurityContextHolder.clearContext();
    }

    private JwtAuthFilter filterWithHandler(int status, byte[] body) {
        server.createContext("/validate-token", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (body == null) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return new JwtAuthFilter(WebClient.builder(), "http://localhost:" + port);
    }

    private MockHttpServletRequest requestWithToken() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader("Authorization", "Bearer test-token");
        return request;
    }

    @Test
    void validTokenPopulatesAuthenticatedSecurityContextAndContinuesTheChain() throws Exception {
        final JwtAuthFilter filter = filterWithHandler(200, VALID_AUTH_JSON.getBytes(StandardCharsets.UTF_8));
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken(), response, chain);

        assertNotNull(chain.getRequest(), "the chain must continue for a valid token");
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated(), "a validated token must produce an AUTHENTICATED token");
        assertTrue(authentication.getPrincipal() instanceof AuthenticationResponseDto.Principal);
        final AuthenticationResponseDto.Principal principal =
                (AuthenticationResponseDto.Principal) authentication.getPrincipal();
        assertEquals("jane", principal.getUsername());
        assertEquals("cus_MINE", principal.getExternalId());
        assertTrue(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_USER"::equals),
                "authorities from validate-token must be mapped onto the authenticated token");
    }

    @Test
    void invalidTokenShortCircuitsWith401AndDoesNotContinueTheChain() throws Exception {
        final JwtAuthFilter filter = filterWithHandler(401, null);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken(), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "the chain must NOT continue after a 401 from the directory service");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void requestWithoutTokenSkipsValidationAndProceeds() throws Exception {
        final JwtAuthFilter filter = filterWithHandler(401, null);
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "a request with no token must not be validated");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
