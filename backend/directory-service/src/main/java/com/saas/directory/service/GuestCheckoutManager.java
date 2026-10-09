package com.saas.directory.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.saas.directory.dto.*;
import com.saas.directory.model.*;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.*;

@Service
public class GuestCheckoutManager {
    private final GuestSessionRepository sessions;
    private final GuestCustomerRepository customers;
    private final AsaasProvisioningJobRepository jobs;
    private final ProfileManager profiles;
    private final ObjectMapper json;
    private final RateLimitStore limits;

    public GuestCheckoutManager(
            GuestSessionRepository sessions,
            GuestCustomerRepository customers,
            AsaasProvisioningJobRepository jobs,
            ProfileManager profiles,
            ObjectMapper json,
            RateLimitStore limits) {
        this.sessions = sessions;
        this.customers = customers;
        this.jobs = jobs;
        this.profiles = profiles;
        this.json = json;
        this.limits = limits;
    }

    @Transactional
    public GuestSessionDto create(String secret) {
        final GuestSession session = sessions.save(
                new GuestSession(GuestSecrets.digest(secret), Instant.now().plusSeconds(86400)));
        return snapshot(session, secret);
    }

    @Transactional(readOnly = true)
    public GuestSessionDto validateOrDie(String secret, String csrf, boolean write) {
        final GuestSession session = findOrDie(secret, false);
        if (write && !GuestSecrets.equal(csrf(secret), csrf))
            throw new org.springframework.security.access.AccessDeniedException("Invalid guest request");
        return snapshot(session, secret);
    }

    @Transactional
    public GuestSessionDto saveDetails(String secret, String csrf, GuestDetailsDto details) {
        final GuestSession session = findOrDie(secret, true);
        requireCsrf(secret, csrf);
        profiles.validateProfile(details.profile());
        final ProfileDto profile = ProfileDto.from(profiles.buildProfile(null, details.profile()));
        final String email = details.email().trim().toLowerCase(Locale.ROOT);
        final GuestCustomer previous = session.getCustomer();
        final ProfileDto previousProfile = previous == null ? null : readProfile(previous.getProfileJson());
        final boolean sameBuyer = previous != null
                && email.equals(previous.getEmail())
                && profile.getCpf().equals(previousProfile.getCpf())
                && profile.getFirstname().equals(previousProfile.getFirstname())
                && profile.getLastname().equals(previousProfile.getLastname());
        if (!sameBuyer) {
            if (session.getAttemptJson() != null)
                throw new IllegalArgumentException("Resume or finish your saved checkout before changing the buyer");
            if (!limits.tryAcquire("guest-provider-global", 60))
                throw new IllegalArgumentException("Guest checkout is busy. Retry shortly");
            final GuestCustomer customer = customers.saveAndFlush(new GuestCustomer(email, write(profile)));
            jobs.save(new AsaasProvisioningJob(customer, PaymentProcessor.ASAAS, Instant.now()));
            session.setCustomer(customer);
        }
        session.setDraftJson(write(profile));
        session.remember(details.remember(), Instant.now());
        sessions.saveAndFlush(session);
        return snapshot(session, secret);
    }

    @Transactional
    public void saveAttempt(
            String secret, String csrf, String attempt, String expectedOrderId, String expectedAttemptKey) {
        final GuestSession session = findOrDie(secret, true);
        requireCsrf(secret, csrf);
        if (session.getCustomer() == null) throw new IllegalArgumentException("Guest details are required");
        if (attempt != null) {
            if (attempt.length() > 60000) throw new IllegalArgumentException("Checkout draft is too large");
            try {
                final com.fasterxml.jackson.databind.JsonNode tree = json.readTree(attempt);
                if (!tree.isObject()
                        || !("guest:" + session.getId())
                                .equals(tree.path("username").asText()))
                    throw new IllegalArgumentException("Invalid checkout draft");
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid checkout draft");
            }
        }
        if (attempt == null && expectedOrderId != null && session.getAttemptJson() != null) {
            try {
                if (!expectedOrderId.equals(json.readTree(session.getAttemptJson())
                        .path("currentOrder")
                        .path("id")
                        .asText())) return;
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Invalid saved checkout");
            }
        }
        if (session.getAttemptJson() != null) {
            try {
                final com.fasterxml.jackson.databind.JsonNode saved = json.readTree(session.getAttemptJson());
                if (expectedAttemptKey != null
                        && !expectedAttemptKey.equals(
                                saved.path("orderIdempotencyKey").asText())) return;
                if (attempt != null
                        && saved.hasNonNull("orderIdempotencyKey")
                        && !saved.path("orderIdempotencyKey")
                                .asText()
                                .equals(json.readTree(attempt)
                                        .path("orderIdempotencyKey")
                                        .asText()))
                    throw new IllegalArgumentException("Resume the saved checkout before starting another one");
                if (attempt == null && expectedOrderId == null && expectedAttemptKey == null)
                    throw new IllegalArgumentException("Identify the checkout to clear");
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Invalid saved checkout");
            }
        }
        session.setAttemptJson(attempt);
        sessions.saveAndFlush(session);
    }

    @Transactional
    public void forget(String secret, String csrf) {
        final GuestSession session = findOrDie(secret, true);
        requireCsrf(secret, csrf);
        sessions.delete(session);
    }

    @Scheduled(fixedDelayString = "${natiart.guest.cleanup-delay-millis:3600000}")
    @Transactional
    public void cleanExpired() {
        sessions.deleteExpired(Instant.now());
    }

    public ProfileDto readProfile(String value) {
        try {
            return json.readValue(value, ProfileDto.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Guest profile is unavailable");
        }
    }

    private GuestSession findOrDie(String secret, boolean lock) {
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}"))
            throw new org.springframework.security.access.AccessDeniedException("Guest session expired");
        final String digest = GuestSecrets.digest(secret);
        final GuestSession session = (lock
                        ? sessions.findByTokenDigestForUpdate(digest)
                        : sessions.findByTokenDigest(digest))
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(
                        () -> new org.springframework.security.access.AccessDeniedException("Guest session expired"));
        return session;
    }

    private void requireCsrf(String secret, String csrf) {
        if (!GuestSecrets.equal(csrf(secret), csrf))
            throw new org.springframework.security.access.AccessDeniedException("Invalid guest request");
    }

    private String csrf(String secret) {
        return GuestSecrets.digest("guest-csrf:" + secret);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid guest details");
        }
    }

    private GuestSessionDto snapshot(GuestSession session, String secret) {
        final GuestCustomer customer = session.getCustomer();
        final AsaasProvisioningStatus status = customer == null
                ? null
                : jobs.findByGuestCustomerIdAndPaymentProcessor(customer.getId(), PaymentProcessor.ASAAS)
                        .map(AsaasProvisioningJob::getStatus)
                        .orElse(null);
        return new GuestSessionDto(
                session.getId(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getEmail(),
                session.getDraftJson() == null ? null : readProfile(session.getDraftJson()),
                csrf(secret),
                customer == null ? null : customer.getProviderCustomerId(),
                status,
                session.getExpiresAt(),
                session.isRemembered(),
                session.getAttemptJson());
    }
}
