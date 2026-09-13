package com.saas.directory.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Recover;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.service.AsaasApiException;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleUserRegistration(UserRegisteredEvent event) {
        LOGGER.info("Asynchronously handling registration for user [{}]", event.username());
        provisioningService.provisionUser(event.username());
    }

    /**
     * Recovery method for handleUserRegistration. This is called when all retry attempts fail.
     * It specifically handles Exception to catch anything the @Retryable annotation was configured for.
     *
     * @param e     The final exception that caused the failure.
     * @param event The original event that was being processed.
     */
    @Recover
    public void recover(Exception e, UserRegisteredEvent event) {
        if (isPermanentProviderFailure(e)) {
            logPermanentFailure(event, e);
            return;
        }

        LOGGER.error(
                "CRITICAL: All retry attempts to register user [{}] with Asaas failed. Manual intervention may be required. Final error: {}",
                event.username(),
                e.getMessage());
        // We could add logic to alert an admin or add to a persistent "dead-letter" queue.
    }

    private void logPermanentFailure(UserRegisteredEvent event, Exception e) {
        LOGGER.error(
                "Unrecoverable payment-provider request for user [{}] will not be retried. Error: {}",
                event.username(),
                e.getMessage());
    }

    private static boolean isPermanentProviderFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AsaasApiException asaasApiException
                    && asaasApiException.getHttpStatus().is4xxClientError()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
