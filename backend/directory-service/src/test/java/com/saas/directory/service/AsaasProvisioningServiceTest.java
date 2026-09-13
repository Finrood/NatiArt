package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.model.AsaasProvisioningJob;
import com.saas.directory.model.AsaasProvisioningStatus;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.AsaasProvisioningJobRepository;
import com.saas.directory.repository.ExternalUserRepository;

class AsaasProvisioningServiceTest {

    @Test
    void reconcilesExistingProviderCustomerBeforeCreatingAnother() throws Exception {
        final UserManager userManager = mock(UserManager.class);
        final AsaasUserManager asaasUserManager = mock(AsaasUserManager.class);
        final ExternalUserRepository externalUserRepository = mock(ExternalUserRepository.class);
        final AsaasProvisioningJobRepository jobRepository = mock(AsaasProvisioningJobRepository.class);
        final User user = new User("customer@example.com", "password");
        final AsaasProvisioningJob job = new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now());
        final AsaasCustomerCreationResponse existing = customerResponse("cus_existing", user.getId());

        when(userManager.getUserOrDie(user.getUsername())).thenReturn(user);
        when(jobRepository.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)).thenReturn(Optional.of(job));
        when(externalUserRepository.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS))
                .thenReturn(Optional.empty());
        when(asaasUserManager.findCustomersByExternalReference(user.getId())).thenReturn(List.of(existing));

        new AsaasProvisioningService(userManager, asaasUserManager, externalUserRepository, jobRepository, 60_000)
                .provisionUser(user.getUsername());

        verify(asaasUserManager).findCustomersByExternalReference(user.getId());
        verify(userManager).addAsaasCustomerIdToUser(user.getUsername(), "cus_existing");
        assertEquals(AsaasProvisioningStatus.SUCCEEDED, job.getStatus());
        assertEquals("cus_existing", job.getProviderCustomerId());
    }

    @Test
    void retryableFailureRemainsDurableAndDoesNotDisappear() throws Exception {
        final UserManager userManager = mock(UserManager.class);
        final AsaasUserManager asaasUserManager = mock(AsaasUserManager.class);
        final ExternalUserRepository externalUserRepository = mock(ExternalUserRepository.class);
        final AsaasProvisioningJobRepository jobRepository = mock(AsaasProvisioningJobRepository.class);
        final User user = new User("customer@example.com", "password");
        final AsaasProvisioningJob job = new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now());

        when(userManager.getUserOrDie(user.getUsername())).thenReturn(user);
        when(jobRepository.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)).thenReturn(Optional.of(job));
        when(externalUserRepository.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS))
                .thenReturn(Optional.empty());
        when(asaasUserManager.findCustomersByExternalReference(user.getId()))
                .thenThrow(new RuntimeException("provider timeout"));

        new AsaasProvisioningService(userManager, asaasUserManager, externalUserRepository, jobRepository, 60_000)
                .provisionUser(user.getUsername());

        assertEquals(AsaasProvisioningStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals("provider timeout", job.getLastError());
    }

    private AsaasCustomerCreationResponse customerResponse(String id, String externalReference) {
        return new AsaasCustomerCreationResponse(
                "customer", id, "2025-01-01", "Test User", "test@example.com", null, null, null, null, null,
                null, null, null, null, null, false, null, externalReference, false, null, null, null, false, null,
                false, null, 0, null, null, null);
    }
}
