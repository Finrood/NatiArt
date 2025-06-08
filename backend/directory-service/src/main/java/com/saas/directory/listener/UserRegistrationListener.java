package com.saas.directory.listener;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.service.AsaasUserManager;
import com.saas.directory.service.UserManager;
import com.saas.directory.service.support.RetryExternalApiCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Recover;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class UserRegistrationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegistrationListener.class);

    private final UserManager userManager;
    private final AsaasUserManager asaasUserManager;

    public UserRegistrationListener(UserManager userManager, AsaasUserManager asaasUserManager) {
        this.userManager = userManager;
        this.asaasUserManager = asaasUserManager;
    }

    /**
     * Handles the user registration event asynchronously.
     * This method is triggered after a new user is committed to the database.
     * It registers the user with the external Asaas payment service and updates
     * the user record with the external customer ID.
     *
     * @param event The event containing the newly registered user's username.
     */
    @Async
    @EventListener
    @Transactional
    @RetryExternalApiCall
    public void handleUserRegistration(UserRegisteredEvent event) throws Exception {
        LOGGER.info("Asynchronously handling registration for user [{}]", event.username());

        final UserDto userDto = UserDto.from(userManager.getUserOrDie(event.username()));
        final AsaasCustomerCreationResponse asaasResponse = asaasUserManager.registerUser(userDto);
        ExternalUser externalUser = userManager.addAsaasCustomerIdToUser(userDto.getUsername(), asaasResponse.getId());

        LOGGER.info("Successfully created Asaas customer [{}] for user [{}]", asaasResponse.getId(), event.username());
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
        if (e instanceof HttpClientErrorException.BadRequest) {
            LOGGER.error(
                    "Unrecoverable 400 Bad Request error for user [{}]. The request is malformed and will not be retried. Error: {}",
                    event.username(),
                    e.getMessage()
            );
            return;
        }

        LOGGER.error(
                "CRITICAL: All retry attempts to register user [{}] with Asaas failed. Manual intervention may be required. Final error: {}",
                event.username(),
                e.getMessage()
        );
        // We could add logic to alert an admin or add to a persistent "dead-letter" queue.
    }
}