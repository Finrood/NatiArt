package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import com.saas.directory.configuration.RequestCorrelationFilter;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.model.AsaasProvisioningJob;
import com.saas.directory.model.AsaasProvisioningStatus;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.AsaasProvisioningJobRepository;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;
import com.saas.directory.service.AsaasProvisioningStateService.Claim;

@SpringBootTest
class AsaasProvisioningTransactionTest {
    @Autowired
    private AsaasProvisioningService provisioningService;

    @Autowired
    private AsaasProvisioningStateService stateService;

    @Autowired
    private AsaasProvisioningJobRepository jobRepository;

    @Autowired
    private ExternalUserRepository externalUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AsaasUserManager provider;

    @Test
    void timeoutAfterCommittedClaimIsRecoveredWithoutAnotherCustomerPost() throws Exception {
        final Role role = roleRepository
                .findRoleByLabel(RoleName.USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.USER)));
        final User user = userRepository.save(new User(UUID.randomUUID() + "@example.test", "password").setRole(role));
        final AsaasProvisioningJob job =
                jobRepository.save(new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now(), "signup-42"));
        when(provider.findCustomersByExternalReference(user.getId())).thenAnswer(invocation -> {
            assertEquals("signup-42", MDC.get(RequestCorrelationFilter.MDC_KEY));
            assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "provider I/O must start after the claim transaction commits");
            assertEquals(
                    AsaasProvisioningStatus.IN_PROGRESS,
                    jobRepository.findById(job.getId()).orElseThrow().getStatus());
            throw new ResourceAccessException("simulated timeout after provider creation");
        });

        provisioningService.provisionUser(user.getUsername());

        final AsaasProvisioningJob pending = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(AsaasProvisioningStatus.PENDING, pending.getStatus());
        assertEquals(1, pending.getAttemptCount());
        assertEquals("signup-42", pending.getCorrelationId());
        assertTrue(pending.getNextAttemptAt().isAfter(Instant.now()));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            final AsaasProvisioningJob due =
                    jobRepository.findByIdForUpdate(job.getId()).orElseThrow();
            due.retryAt(Instant.now().minusSeconds(1), "retry now");
        });
        final AsaasCustomerCreationResponse existing = mock(AsaasCustomerCreationResponse.class);
        when(existing.getId()).thenReturn("cus_reconciled");
        doAnswer(invocation -> {
                    assertEquals("signup-42", MDC.get(RequestCorrelationFilter.MDC_KEY));
                    return List.of(existing);
                })
                .when(provider)
                .findCustomersByExternalReference(user.getId());

        provisioningService.processDueJobs();

        final AsaasProvisioningJob succeeded =
                jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(AsaasProvisioningStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals("cus_reconciled", succeeded.getProviderCustomerId());
        assertEquals(
                "cus_reconciled",
                externalUserRepository
                        .findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                        .orElseThrow()
                        .getExternalId());
        verify(provider, never()).registerUser(any());
        provisioningService.provisionUser(user.getUsername());
        verify(provider, times(2)).findCustomersByExternalReference(user.getId());
    }

    @Test
    void lateWorkerCannotCommitAfterAnotherClaimTakesOver() {
        final Role role = roleRepository
                .findRoleByLabel(RoleName.USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.USER)));
        final User user = userRepository.save(new User(UUID.randomUUID() + "@example.test", "password").setRole(role));
        final AsaasProvisioningJob job =
                jobRepository.save(new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now()));

        final Claim first = stateService.claimForUsername(user.getUsername());
        assertNotNull(first);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            final AsaasProvisioningJob expired =
                    jobRepository.findByIdForUpdate(job.getId()).orElseThrow();
            expired.retryAt(Instant.now().minusSeconds(1), "lease expired");
        });
        final Claim second = stateService.claimById(job.getId());
        assertNotNull(second);

        stateService.succeeded(first, "cus_stale");
        assertEquals(
                AsaasProvisioningStatus.IN_PROGRESS,
                jobRepository.findById(job.getId()).orElseThrow().getStatus());
        assertTrue(externalUserRepository
                .findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                .isEmpty());

        stateService.succeeded(second, "cus_current");
        assertEquals(
                "cus_current",
                externalUserRepository
                        .findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                        .orElseThrow()
                        .getExternalId());
    }
}
