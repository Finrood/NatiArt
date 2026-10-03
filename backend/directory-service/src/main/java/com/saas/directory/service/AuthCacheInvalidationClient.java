package com.saas.directory.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Sends account revocation to product-service after the directory commit. */
@Component
public class AuthCacheInvalidationClient {
    private static final String SECRET_HEADER = "X-NatiArt-Internal-Secret";

    private final String productServiceUrl;
    private final String sharedSecret;
    private final RestTemplate restTemplate;

    @Autowired
    public AuthCacheInvalidationClient(
            @Value("${natiart.product-service.url:}") String productServiceUrl,
            @Value("${natiart.auth-cache.invalidation-secret:}") String sharedSecret) {
        this(productServiceUrl, sharedSecret, createRestTemplate());
    }

    AuthCacheInvalidationClient(String productServiceUrl, String sharedSecret, RestTemplate restTemplate) {
        if (productServiceUrl != null
                && !productServiceUrl.isBlank()
                && (sharedSecret == null || sharedSecret.isBlank())) {
            throw new IllegalStateException("Product auth-cache invalidation secret is required");
        }
        this.productServiceUrl = productServiceUrl;
        this.sharedSecret = sharedSecret;
        this.restTemplate = restTemplate;
    }

    /** Returns false only in local setups without a product-service endpoint. */
    public boolean invalidateUser(String userId) {
        if (productServiceUrl == null || productServiceUrl.isBlank()) {
            return false;
        }
        final HttpHeaders headers = new HttpHeaders();
        headers.set(SECRET_HEADER, sharedSecret);
        final ResponseEntity<Void> response = restTemplate.exchange(
                productServiceUrl.replaceAll("/+$", "") + "/internal/auth-cache/users/" + userId + "/invalidate",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Product auth-cache invalidation failed");
        }
        return true;
    }

    private static RestTemplate createRestTemplate() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(2_000);
        return new RestTemplate(factory);
    }
}
