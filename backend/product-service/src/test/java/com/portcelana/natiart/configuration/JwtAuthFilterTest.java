package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import com.portcelana.natiart.dto.AuthenticationResponseDto;

import reactor.core.publisher.Mono;

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

    private JwtAuthFilter filterWithHandler(int status, byte[] body, long delayMillis) {
        server.createContext("/validate-token", exchange -> {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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
        return requestWithToken("GET", "/products");
    }

    private MockHttpServletRequest requestWithToken(String method, String path) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
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
        assertTrue(
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_USER"::equals),
                "authorities from validate-token must be mapped onto the authenticated token");
    }

    @Test
    void invalidTokenOnPublicReadContinuesAsAnonymous() throws Exception {
        final JwtAuthFilter filter = filterWithHandler(401, null);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken(), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "public reads must continue anonymously after token rejection");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidTokenOnProtectedWriteStillShortCircuitsWith401() throws Exception {
        final JwtAuthFilter filter = filterWithHandler(401, null);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("POST", "/cart/item/p1/add"), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "protected writes must not continue with an invalid token");
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

    @Test
    void directoryServiceOutageResponds503Not401() throws Exception {
        // Simulate a real outage: bind a socket, then close it so connections are promptly refused.
        final java.net.ServerSocket socket =
                new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("localhost"));
        port = socket.getLocalPort();
        socket.close();

        final JwtAuthFilter filter = new JwtAuthFilter(WebClient.builder(), "http://localhost:" + port);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        final long start = System.currentTimeMillis();
        filter.doFilter(requestWithToken("POST", "/cart/item/p1/add"), response, chain);
        final long elapsed = System.currentTimeMillis() - start;

        assertEquals(503, response.getStatus(), "a directory-service outage must map to 503, not a mass 401");
        assertNull(chain.getRequest());
        assertTrue(elapsed < 5_000, "connection refused should fail fast, took " + elapsed + "ms");
    }

    @Test
    void slowDirectoryServiceIsBoundedByTheFiveSecondTimeout() throws Exception {
        final JwtAuthFilter filter = filterWithHandler(200, VALID_AUTH_JSON.getBytes(StandardCharsets.UTF_8), 7_000);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        final long start = System.currentTimeMillis();
        filter.doFilter(requestWithToken("POST", "/cart/item/p1/add"), response, chain);
        final long elapsed = System.currentTimeMillis() - start;

        assertEquals(503, response.getStatus(), "a hung validation call must time out into 503");
        assertNull(chain.getRequest());
        assertTrue(elapsed < 7_000, "5s timeout must fire before the 7s handler responds, took " + elapsed + "ms");
    }

    @Test
    void reusesASingleWebClientAcrossRequests() throws Exception {
        final WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        final WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        final WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        final AuthenticationResponseDto dto =
                new ObjectMapper().readValue(VALID_AUTH_JSON, AuthenticationResponseDto.class);

        final WebClient.Builder builder = mock(WebClient.Builder.class);
        final WebClient webClient = mock(WebClient.class);
        when(builder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any())).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AuthenticationResponseDto.class)).thenReturn(Mono.just(dto));

        final JwtAuthFilter filter = new JwtAuthFilter(builder, "http://localhost:1");
        for (int i = 0; i < 2; i++) {
            final MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestWithToken(), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            SecurityContextHolder.clearContext();
        }

        verify(builder, times(1)).build();
    }
}
