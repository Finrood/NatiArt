package com.saas.directory.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.saas.directory.configuration.RequestCorrelationFilter;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.service.AsaasProvisioningService;

@Component
public class UserRegistrationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegistrationListener.class);

    private final AsaasProvisioningService provisioningService;

    public UserRegistrationListener(AsaasProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    /** Starts the durable provisioning job after registration; the scheduler recovers missed wake-ups. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistration(UserRegisteredEvent event) {
        final String previous = MDC.get(RequestCorrelationFilter.MDC_KEY);
        final String requestId = RequestCorrelationFilter.safeCorrelationId(event.correlationId());
        MDC.put(RequestCorrelationFilter.MDC_KEY, requestId);
        try {
            LOGGER.info("Provisioning wake-up started requestId={}", requestId);
            provisioningService.provisionUser(event.username());
        } catch (Exception exception) {
            LOGGER.warn(
                    "Provisioning wake-up failed requestId={} failureType={}; durable job remains retryable",
                    requestId,
                    exception.getClass().getSimpleName());
        } finally {
            if (previous == null) {
                MDC.remove(RequestCorrelationFilter.MDC_KEY);
            } else {
                MDC.put(RequestCorrelationFilter.MDC_KEY, previous);
            }
        }
    }
}
