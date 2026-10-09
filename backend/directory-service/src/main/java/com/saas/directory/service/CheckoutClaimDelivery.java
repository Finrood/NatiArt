package com.saas.directory.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.saas.directory.repository.CheckoutClaimRepository;

/** Retries the committed account/order link after service outages or process restarts. */
@Service
public class CheckoutClaimDelivery {
    private final CheckoutClaimRepository claims;
    private final String productUrl;
    private final String secret;
    private final org.springframework.web.client.RestTemplate client;

    public CheckoutClaimDelivery(
            CheckoutClaimRepository claims,
            @Value("${natiart.product-service.url:}") String productUrl,
            @Value("${natiart.auth-cache.invalidation-secret:}") String secret) {
        this.claims = claims;
        this.productUrl = productUrl;
        this.secret = secret;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = new org.springframework.web.client.RestTemplate(factory);
    }

    public record Link(String claimId, String accountId, String email, Instant cutoff) {}

    @Scheduled(fixedDelayString = "${natiart.guest.claim-delivery-delay-millis:5000}")
    public void deliver() {
        if (productUrl == null || productUrl.isBlank() || secret == null || secret.isBlank()) return;
        for (final String id : claims.findDueIds(Instant.now(), PageRequest.of(0, 20))) {
            final com.saas.directory.model.CheckoutClaim claim =
                    claims.findById(id).orElse(null);
            if (claim == null) continue;
            final HttpHeaders headers = new HttpHeaders();
            headers.set("X-NatiArt-Internal-Secret", secret);
            try {
                client.postForEntity(
                        productUrl + "/internal/guest/claim-orders",
                        new HttpEntity<>(
                                new Link(claim.getId(), claim.getAccountId(), claim.getEmail(), claim.getCutoff()),
                                headers),
                        Void.class);
                claim.delivered();
            } catch (org.springframework.web.client.RestClientException exception) {
                claim.retry();
            }
            try {
                claims.save(claim);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException concurrentDelivery) {
                /* a committed peer won */
            }
        }
    }
}
