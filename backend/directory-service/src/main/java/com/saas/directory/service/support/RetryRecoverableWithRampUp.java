package com.saas.directory.service.support;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retryable(
        retryFor = {
                TransientDataAccessException.class,
                RecoverableDataAccessException.class,
                DataSourceLookupFailureException.class,
                OptimisticLockException.class,
                StaleStateException.class,
                ConstraintViolationException.class,
                DataIntegrityViolationException.class
        },
        maxAttempts = 10,
        backoff = @Backoff(delay = 100, maxDelay = 60000, multiplier = 5))
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RetryRecoverableWithRampUp {

}
