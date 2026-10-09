package com.portcelana.natiart.service;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.GuestCheckoutDto;

/** Guest credentials are validated independently of ordinary account authentication. */
@Service
public class GuestCheckoutClient {
    private final String directoryUrl;
    private final String secret;
    private final boolean secure;
    private final RestTemplate client;

    public GuestCheckoutClient(
            @Value("${directory.service.url}") String directoryUrl,
            @Value("${natiart.auth-cache.invalidation-secret:}") String secret,
            Environment environment,
            @Value("${natiart.guest.secure-cookie:#{null}}") Boolean secureCookie) {
        this.directoryUrl = directoryUrl;
        this.secret = secret;
        this.secure = secureCookie != null ? secureCookie : !environment.matchesProfiles("local-h2", "test");
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = new RestTemplate(factory);
    }

    public record Validation(String token, String csrfToken, boolean write) {
        @Override
        public String toString() {
            return "Validation[redacted]";
        }
    }

    public record TrackingScope(String email, java.time.Instant cutoff, java.time.Instant expiresAt) {}

    public TrackingScope trackingOrDie(HttpServletRequest request) {
        String token = null;
        final String name = secure ? "__Host-natiart-guest-orders" : "natiart-guest-orders";
        if (request.getCookies() != null)
            for (final Cookie cookie : request.getCookies())
                if (name.equals(cookie.getName())) token = cookie.getValue();
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new UserNotAllowedException("Order link expired");
        if (secret == null || secret.isBlank())
            throw new UpstreamServiceException("Order tracking unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-NatiArt-Internal-Secret", secret);
        try {
            final TrackingScope scope = client.postForObject(
                    directoryUrl + "/internal/guest/tracking/validate",
                    new HttpEntity<>(java.util.Map.of("token", token), headers),
                    TrackingScope.class);
            if (scope == null
                    || scope.email() == null
                    || scope.cutoff() == null
                    || scope.expiresAt() == null
                    || !scope.expiresAt().isAfter(java.time.Instant.now()))
                throw new UserNotAllowedException("Order link expired");
            return scope;
        } catch (HttpClientErrorException exception) {
            throw new UserNotAllowedException("Order link expired");
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Order tracking unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public GuestCheckoutDto validateOrDie(HttpServletRequest request, boolean write) {
        String token = null;
        final String cookieName = secure ? "__Host-natiart-guest" : "natiart-guest";
        if (request.getCookies() != null) {
            for (final Cookie cookie : request.getCookies())
                if (cookieName.equals(cookie.getName())) token = cookie.getValue();
        }
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new UserNotAllowedException("Guest session expired");
        if (secret == null || secret.isBlank())
            throw new UpstreamServiceException("Guest checkout unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-NatiArt-Internal-Secret", secret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            final GuestCheckoutDto guest = client.postForObject(
                    directoryUrl + "/internal/guest/validate",
                    new HttpEntity<>(new Validation(token, request.getHeader("X-Guest-CSRF"), write), headers),
                    GuestCheckoutDto.class);
            if (guest == null
                    || guest.customerId() == null
                    || guest.externalId() == null
                    || guest.externalId().isBlank())
                throw new UserNotAllowedException("Guest payment profile is being prepared");
            return guest;
        } catch (HttpClientErrorException exception) {
            throw new UserNotAllowedException("Guest session expired");
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Guest checkout unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
