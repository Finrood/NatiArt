package com.portcelana.natiart.configuration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Resets the private provider to match the fresh QA database before accepting traffic. */
@Component
@Profile("qa-h2")
public class QaStartup implements ApplicationRunner {
    private final Environment environment;
    private final URI resetUrl;
    private final String apiKey;
    private final RestTemplate client;

    @Autowired
    public QaStartup(
            Environment environment,
            @Value("${natiart.qa.provider-reset-url:}") String resetUrl,
            @Value("${natiart.payment.asaas.apikey}") String apiKey) {
        this(environment, resetUrl, apiKey, createClient());
    }

    QaStartup(Environment environment, String resetUrl, String apiKey, RestTemplate client) {
        this.environment = environment;
        this.resetUrl = URI.create(resetUrl);
        if (!"http".equals(this.resetUrl.getScheme())
                || this.resetUrl.getHost() == null
                || this.resetUrl.getUserInfo() != null
                || this.resetUrl.getQuery() != null
                || this.resetUrl.getFragment() != null
                || !this.resetUrl.getPath().startsWith("/internal/reset-")
                || apiKey == null
                || apiKey.isBlank()) {
            throw new IllegalArgumentException("QA requires an explicit private provider reset URL and key");
        }
        this.apiKey = apiKey;
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        requireQaProfile();
        final HttpHeaders headers = new HttpHeaders();
        headers.set("access_token", apiKey);
        client.postForEntity(resetUrl, new HttpEntity<>(null, headers), Void.class);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markReady() throws IOException {
        requireQaProfile();
        Files.writeString(Path.of("/tmp/natiart-qa-ready"), "ready");
    }

    private void requireQaProfile() {
        if (!environment.acceptsProfiles(Profiles.of("local-h2 & !production"))) {
            throw new IllegalStateException("qa-h2 requires local-h2 and cannot run with production");
        }
    }

    private static RestTemplate createClient() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
