package com.saas.directory.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Sends QA recovery links only to the configured private fixture inbox. */
@Service
@Profile("qa-h2 & !production")
public class QaPasswordResetNotificationSender implements PasswordResetNotificationSender {
    private final URI url;
    private final String controlToken;
    private final RestTemplate client;

    @Autowired
    public QaPasswordResetNotificationSender(
            @Value("${natiart.qa.notifications-url:}") String url,
            @Value("${natiart.qa.control-token:}") String controlToken) {
        this(url, controlToken, createClient());
    }

    QaPasswordResetNotificationSender(String url, String controlToken, RestTemplate client) {
        this.url = URI.create(url);
        if (!("http".equals(this.url.getScheme()) || "https".equals(this.url.getScheme()))
                || this.url.getHost() == null
                || this.url.getUserInfo() != null
                || this.url.getQuery() != null
                || this.url.getFragment() != null
                || controlToken == null
                || controlToken.isBlank()) {
            throw new IllegalArgumentException("QA requires a private notification URL and control token");
        }
        this.controlToken = controlToken;
        this.client = client;
    }

    @Override
    public void send(PasswordResetNotification notification) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-NatiArt-QA-Token", controlToken);
        try {
            client.postForEntity(url, new HttpEntity<>(notification, headers), Void.class);
        } catch (RestClientException deliveryFailure) {
            // Preserve the controller's uniform recovery response without retaining link details.
            throw new MailSendException("QA recovery delivery failed");
        }
    }

    private static RestTemplate createClient() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
