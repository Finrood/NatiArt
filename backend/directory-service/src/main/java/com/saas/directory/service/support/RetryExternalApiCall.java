package com.saas.directory.service.support;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A custom annotation to handle retries for external API calls that may fail due to
 * transient network issues, timeouts, or temporary server-side errors (5xx).
 * It will not retry on client errors (4xx) like 'Bad Request', as these are unlikely
 * to be resolved by retrying.
 */
@Retryable(
        retryFor = {
                HttpServerErrorException.class,
                ResourceAccessException.class,
                IOException.class
        },
        noRetryFor = {
                HttpClientErrorException.class // Do not retry on 4xx client errors
        },
        maxAttempts = 20,
        backoff = @Backoff(
                delay = 100,
                multiplier = 5,
                maxDelay = 3000000
        )
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RetryExternalApiCall {
}
