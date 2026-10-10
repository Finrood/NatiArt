package com.portcelana.natiart.service;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Routes QA purchase updates only to the private fixture inbox, keyed for replay. */
public class QaOrderNotificationSender implements OrderNotificationSender {
    private final URI uri;
    private final String controlToken;
    private final RestTemplate client;

    public QaOrderNotificationSender(String url, String controlToken) {
        uri = URI.create(url);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || controlToken.isBlank()) {
            throw new IllegalArgumentException("QA purchase inbox configuration is invalid");
        }
        this.controlToken = controlToken;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = new RestTemplate(factory);
    }

    @Override
    public void send(OrderMailMessage message) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-NatiArt-QA-Token", controlToken);
        client.postForEntity(
                uri,
                new HttpEntity<>(
                        Map.of(
                                "id",
                                message.id(),
                                "recipient",
                                message.recipient(),
                                "subject",
                                message.subject(),
                                "body",
                                message.body()),
                        headers),
                Void.class);
    }
}
