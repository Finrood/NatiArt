package com.saas.directory.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.UserDto;
import com.saas.directory.model.AsaasProvisioningJob;
import com.saas.directory.model.AsaasProvisioningStatus;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.AsaasProvisioningJobRepository;
import com.saas.directory.repository.ExternalUserRepository;

/** Commits a provisioning claim before the worker contacts the payment provider. */
@Service
public class AsaasProvisioningStateService {
    private static final long MAX_RETRY_DELAY_SECONDS = 3_600L;

    private final UserManager userManager;
    private final ExternalUserRepository externalUserRepository;
    private final AsaasProvisioningJobRepository jobRepository;
    private final Duration lease;
    private final com.saas.directory.repository.GuestCustomerRepository guestCustomers;
    private final GuestCheckoutManager guestCheckout;

    public AsaasProvisioningStateService(
            UserManager userManager,
            ExternalUserRepository externalUserRepository,
            AsaasProvisioningJobRepository jobRepository,
            com.saas.directory.repository.GuestCustomerRepository guestCustomers,
            GuestCheckoutManager guestCheckout,
            @Value("${saas.asaas.provisioning.lease-millis:120000}") long leaseMillis) {
        if (leaseMillis < 30_000) {
            throw new IllegalArgumentException("Asaas provisioning lease must exceed the provider HTTP timeout");
        }
        this.guestCustomers = guestCustomers;
        this.guestCheckout = guestCheckout;
        this.userManager = userManager;
        this.externalUserRepository = externalUserRepository;
        this.jobRepository = jobRepository;
        this.lease = Duration.ofMillis(leaseMillis);
    }

    @Transactional
    public Claim claimForUsername(String username) {
        final User user = userManager
                .getUserForUpdate(username)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        final AsaasProvisioningJob job = jobRepository
                .findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                .orElseGet(() -> jobRepository.saveAndFlush(
                        new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now())));
        return claim(job);
    }

    @Transactional
    public Claim claimForGuest(String customerId) {
        final com.saas.directory.model.GuestCustomer customer = guestCustomers
                .findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        final AsaasProvisioningJob job = jobRepository
                .findByGuestCustomerIdAndPaymentProcessor(customer.getId(), PaymentProcessor.ASAAS)
                .orElseThrow(() -> new ResourceNotFoundException("Provisioning job not found"));
        return jobRepository.findByIdForUpdate(job.getId()).map(this::claim).orElse(null);
    }

    @Transactional
    public Claim claimById(String jobId) {
        return jobRepository.findByIdForUpdate(jobId).map(this::claim).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<String> dueJobIds() {
        return jobRepository
                .findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        List.of(AsaasProvisioningStatus.PENDING, AsaasProvisioningStatus.IN_PROGRESS), Instant.now())
                .stream()
                .map(AsaasProvisioningJob::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProvisioningSnapshot> statusForUser(String userId) {
        return jobRepository
                .findByUserIdAndPaymentProcessor(userId, PaymentProcessor.ASAAS)
                .map(job -> new ProvisioningSnapshot(job.getStatus(), job.getNextAttemptAt()));
    }

    @Transactional
    public void succeeded(Claim claim, String customerId) {
        // Account edits take the user lock before the job lock; keep the same order for FK writes.
        if (claim.username() != null) {
            userManager
                    .getUserForUpdate(claim.username())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        } else {
            guestCustomers
                    .findByIdForUpdate(claim.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        }
        final AsaasProvisioningJob job =
                jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (!ownsClaim(job, claim)) {
            return;
        }
        if (claim.username() != null) {
            userManager.addAsaasCustomerIdToUser(claim.username(), customerId);
        } else {
            job.getGuestCustomer().setProviderCustomerId(customerId);
            guestCustomers.saveAndFlush(job.getGuestCustomer());
        }
        job.markSucceeded(customerId);
        jobRepository.saveAndFlush(job);
    }

    @Transactional
    public void failed(Claim claim, String reason) {
        final AsaasProvisioningJob job =
                jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (!ownsClaim(job, claim)) {
            return;
        }
        job.markFailed(reason);
        jobRepository.saveAndFlush(job);
    }

    @Transactional
    public void retry(Claim claim, String reason) {
        final AsaasProvisioningJob job =
                jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (!ownsClaim(job, claim)) {
            return;
        }
        final long multiplier = 1L << Math.min(job.getAttemptCount(), 7);
        final long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, 30L * multiplier);
        job.retryAt(Instant.now().plusSeconds(delaySeconds), reason);
        jobRepository.saveAndFlush(job);
    }

    private Claim claim(AsaasProvisioningJob job) {
        final Instant now = Instant.now();
        if (job.getStatus() == AsaasProvisioningStatus.SUCCEEDED
                || job.getStatus() == AsaasProvisioningStatus.FAILED
                || job.getNextAttemptAt().isAfter(now)) {
            return null;
        }
        if (job.getGuestCustomer() != null) {
            final com.saas.directory.model.GuestCustomer customer = job.getGuestCustomer();
            if (customer.getProviderCustomerId() != null) {
                job.markSucceeded(customer.getProviderCustomerId());
                jobRepository.saveAndFlush(job);
                return null;
            }
            job.claim(now, now.plus(lease));
            jobRepository.saveAndFlush(job);
            return new Claim(
                    job.getId(),
                    job.getAttemptCount(),
                    customer.getId(),
                    null,
                    new UserDto()
                            .setId(customer.getId())
                            .setUsername(customer.getEmail())
                            .setProfile(guestCheckout.readProfile(customer.getProfileJson())),
                    job.ensureCorrelationId());
        }
        final User user = job.getUser();
        final Optional<ExternalUser> mapped =
                externalUserRepository.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS);
        if (mapped.isPresent()) {
            job.markSucceeded(mapped.get().getExternalId());
            jobRepository.saveAndFlush(job);
            return null;
        }
        job.claim(now, now.plus(lease));
        final String requestId = job.ensureCorrelationId();
        jobRepository.saveAndFlush(job);
        return new Claim(
                job.getId(),
                job.getAttemptCount(),
                user.getId(),
                user.getUsername(),
                UserDto.from(user, null),
                requestId);
    }

    private boolean ownsClaim(AsaasProvisioningJob job, Claim claim) {
        return job != null
                && job.getStatus() == AsaasProvisioningStatus.IN_PROGRESS
                && job.getAttemptCount() == claim.attemptCount();
    }

    public record Claim(
            String jobId, int attemptCount, String userId, String username, UserDto customer, String correlationId) {
        public Claim(String jobId, int attemptCount, String userId, String username, UserDto customer) {
            this(
                    jobId,
                    attemptCount,
                    userId,
                    username,
                    customer,
                    java.util.UUID.randomUUID().toString());
        }
    }

    public record ProvisioningSnapshot(AsaasProvisioningStatus status, Instant nextAttemptAt) {}
}
