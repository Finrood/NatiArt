package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=local-h2",
            "spring.datasource.url=jdbc:h2:mem:quota-filter-order-test;DB_CLOSE_DELAY=-1",
            "spring.sql.init.mode=never",
            "saas.security.rate-limit.max-requests-per-minute=2",
            "saas.security.rate-limit.internal-validation-max-requests-per-minute=5"
        })
class RateLimitFilterHttpTest {
    @Value("${local.server.port}")
    private int port;

    @Test
    void rejectsPublicTokenProbesAtThePublicLimitBeforeJwtValidation() throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertEquals(401, validate(client, false).statusCode());
            assertEquals(401, validate(client, false).statusCode());

            final HttpResponse<Void> blocked = validate(client, false);
            assertEquals(429, blocked.statusCode());
            assertEquals("60", blocked.headers().firstValue("Retry-After").orElseThrow());

            for (int attempt = 0; attempt < 3; attempt++) {
                assertEquals(401, validate(client, true).statusCode());
            }
            assertEquals(429, validate(client, false).statusCode());
        }
    }

    private HttpResponse<Void> validate(HttpClient client, boolean internal) throws IOException, InterruptedException {
        final String signingSecret =
                Base64.getEncoder().encodeToString("local-development-only-secret".getBytes(StandardCharsets.UTF_8));
        final String unregisteredToken = JWT.create()
                .withIssuer("quota-probe")
                .withJWTId("quota-probe-jti")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(signingSecret));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/validate-token"))
                .header("Authorization", "Bearer " + unregisteredToken)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (internal) {
            request.header(RateLimitFilter.INTERNAL_SERVICE_TOKEN_HEADER, "local-development-validation-secret");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }
}
