package com.saas.directory.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.service.AsaasProvisioningService;

@Component
public class UserRegistrationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegistrationListener.class);

    private final AsaasProvisioningService provisioningService;

    public UserRegistrationListener(AsaasProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    /**
     * Starts the durable provisioning job after registration. The scheduler can
     * recover the same job if this best-effort wake-up is missed.
     *
     * @param event The event containing the newly registered user's username.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistration(UserRegisteredEvent event) {
        LOGGER.info("Asynchronously handling registration for user [{}]", event.username());
        try {
            provisioningService.provisionUser(event.username());
        } catch (Exception exception) {
            // The committed job remains eligible for the scheduler after its lease.
            LOGGER.warn("Provisioning wake-up failed; the durable job remains available for retry");
        }
    }
}
