package com.saas.directory.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.saas.directory.configuration.RequestCorrelationFilter;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.service.AsaasProvisioningStateService.Claim;

/** Reconciles and creates payment customers outside the job-claim transaction. */
@Service
public class AsaasProvisioningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsaasProvisioningService.class);
    private final AsaasProvisioningStateService state;
    private final AsaasUserManager asaasUserManager;

    public AsaasProvisioningService(AsaasProvisioningStateService state, AsaasUserManager asaasUserManager) {
        this.state = state;
        this.asaasUserManager = asaasUserManager;
    }

    public void provisionUser(String username) {
        process(state.claimForUsername(username));
    }

    public void provisionGuest(String customerId) {
        process(state.claimForGuest(customerId));
    }

    @Scheduled(fixedDelayString = "${saas.asaas.provisioning.fixed-delay-millis:30000}")
    public void processDueJobs() {
        for (String jobId : state.dueJobIds()) {
            process(state.claimById(jobId));
        }
    }

    private void process(Claim claim) {
        if (claim == null) {
            return;
        }
        final String requestId = RequestCorrelationFilter.safeCorrelationId(claim.correlationId());
        final String previous = MDC.get(RequestCorrelationFilter.MDC_KEY);
        MDC.put(RequestCorrelationFilter.MDC_KEY, requestId);
        try {
            LOGGER.info("Provisioning attempt started requestId={} attempt={}", requestId, claim.attemptCount());
            final List<AsaasCustomerCreationResponse> existing =
                    asaasUserManager.findCustomersByExternalReference(claim.userId());
            if (existing.size() > 1) {
                state.failed(claim, "Multiple provider customers require manual reconciliation");
                LOGGER.warn("Provisioning stopped requestId={} reason=ambiguous-provider-customers", requestId);
                return;
            }
            final AsaasCustomerCreationResponse response = existing.isEmpty()
                    ? (claim.username() == null
                            ? asaasUserManager.registerCustomer(claim.customer(), true)
                            : asaasUserManager.registerUser(claim.customer()))
                    : existing.getFirst();
            if (response == null || response.getId() == null || response.getId().isBlank()) {
                state.retry(claim, "Payment provider returned no customer ID");
                LOGGER.warn("Provisioning scheduled retry requestId={} reason=empty-provider-id", requestId);
                return;
            }
            if (claim.username() == null
                    && (!claim.userId().equals(response.getExternalReference())
                            || response.isDeleted()
                            || !claim.customer().getProfile().getCpf().equals(response.getCpfCnpj()))) {
                state.failed(claim, "Payment provider returned a different payer");
                return;
            }
            state.succeeded(claim, response.getId());
            LOGGER.info("Provisioning succeeded requestId={}", requestId);
        } catch (AsaasApiException exception) {
            final HttpStatus status = exception.getHttpStatus();
            if (status.is4xxClientError()
                    && status != HttpStatus.REQUEST_TIMEOUT
                    && status != HttpStatus.TOO_MANY_REQUESTS) {
                state.failed(claim, "Payment provider rejected customer details");
                LOGGER.warn("Provisioning failed requestId={} providerStatus={}", requestId, status.value());
            } else {
                state.retry(claim, "Payment provider temporarily unavailable");
                LOGGER.warn("Provisioning scheduled retry requestId={} providerStatus={}", requestId, status.value());
            }
        } catch (Exception exception) {
            state.retry(claim, "Payment provider outcome needs reconciliation");
            LOGGER.warn(
                    "Provisioning scheduled retry requestId={} failureType={}",
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
