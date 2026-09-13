package com.saas.directory.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.model.AsaasProvisioningJob;
import com.saas.directory.model.AsaasProvisioningStatus;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.AsaasProvisioningJobRepository;
import com.saas.directory.repository.ExternalUserRepository;

/** Persists and processes Asaas customer provisioning work independently of registration events. */
@Service
public class AsaasProvisioningService {
    private static final long MAX_RETRY_DELAY_SECONDS = 3_600L;

    private final UserManager userManager;
    private final AsaasUserManager asaasUserManager;
    private final ExternalUserRepository externalUserRepository;
    private final AsaasProvisioningJobRepository jobRepository;
    private final Duration lease;

    public AsaasProvisioningService(
            UserManager userManager,
            AsaasUserManager asaasUserManager,
            ExternalUserRepository externalUserRepository,
            AsaasProvisioningJobRepository jobRepository,
            @Value("${saas.asaas.provisioning.lease-millis:120000}") long leaseMillis) {
        if (leaseMillis < 1) {
            throw new IllegalArgumentException("Asaas provisioning lease must be positive");
        }
        this.userManager = userManager;
        this.asaasUserManager = asaasUserManager;
        this.externalUserRepository = externalUserRepository;
        this.jobRepository = jobRepository;
        this.lease = Duration.ofMillis(leaseMillis);
    }

    @Transactional
    public void provisionUser(String username) {
        final User user = userManager.getUserOrDie(username);
        final AsaasProvisioningJob job = jobRepository
                .findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                .orElseGet(() -> jobRepository.save(new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now())));
        processJob(job);
    }

    @Scheduled(fixedDelayString = "${saas.asaas.provisioning.fixed-delay-millis:30000}")
    @Transactional
    public void processDueJobs() {
        final Instant now = Instant.now();
        final List<AsaasProvisioningJob> jobs = jobRepository
                .findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        List.of(AsaasProvisioningStatus.PENDING, AsaasProvisioningStatus.IN_PROGRESS), now);
        jobs.forEach(this::processJob);
    }

    private void processJob(AsaasProvisioningJob job) {
        final Instant now = Instant.now();
        if (job.getStatus() == AsaasProvisioningStatus.SUCCEEDED
                || job.getStatus() == AsaasProvisioningStatus.FAILED
                || (job.getStatus() == AsaasProvisioningStatus.IN_PROGRESS && job.getNextAttemptAt().isAfter(now))) {
            return;
        }

        job.claim(now, now.plus(lease));
        final User user = job.getUser();
        final Optional<ExternalUser> mappedCustomer = externalUserRepository.findByUserAndPaymentProcessor(
                user, PaymentProcessor.ASAAS);
        if (mappedCustomer.isPresent()) {
            job.markSucceeded(mappedCustomer.get().getExternalId());
            return;
        }

        try {
            final List<AsaasCustomerCreationResponse> existingCustomers =
                    asaasUserManager.findCustomersByExternalReference(user.getId());
            if (existingCustomers.size() > 1) {
                throw new IllegalStateException("Multiple payment customers match the provisioning reference");
            }
            final AsaasCustomerCreationResponse response = existingCustomers.isEmpty()
                    ? asaasUserManager.registerUser(UserDto.from(user, null))
                    : existingCustomers.get(0);
            if (response == null || response.getId() == null || response.getId().isBlank()) {
                throw new IllegalStateException("Payment provider returned no customer id");
            }
            userManager.addAsaasCustomerIdToUser(user.getUsername(), response.getId());
            job.markSucceeded(response.getId());
        } catch (AsaasApiException exception) {
            if (exception.getHttpStatus().is4xxClientError()) {
                job.markFailed(exception.getMessage());
            } else {
                scheduleRetry(job, exception);
            }
        } catch (Exception exception) {
            scheduleRetry(job, exception);
        }
    }

    private void scheduleRetry(AsaasProvisioningJob job, Exception exception) {
        final long multiplier = 1L << Math.min(job.getAttemptCount(), 7);
        final long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, 30L * multiplier);
        job.retryAt(Instant.now().plusSeconds(delaySeconds), exception.getMessage());
    }
}
