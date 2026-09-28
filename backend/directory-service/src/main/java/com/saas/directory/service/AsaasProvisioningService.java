package com.saas.directory.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.service.AsaasProvisioningStateService.Claim;

/** Reconciles and creates payment customers outside the job-claim transaction. */
@Service
public class AsaasProvisioningService {
    private final AsaasProvisioningStateService state;
    private final AsaasUserManager asaasUserManager;

    public AsaasProvisioningService(AsaasProvisioningStateService state, AsaasUserManager asaasUserManager) {
        this.state = state;
        this.asaasUserManager = asaasUserManager;
    }

    public void provisionUser(String username) {
        process(state.claimForUsername(username));
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
        try {
            final List<AsaasCustomerCreationResponse> existing =
                    asaasUserManager.findCustomersByExternalReference(claim.userId());
            if (existing.size() > 1) {
                state.failed(claim, "Multiple provider customers require manual reconciliation");
                return;
            }
            final AsaasCustomerCreationResponse response =
                    existing.isEmpty() ? asaasUserManager.registerUser(claim.customer()) : existing.getFirst();
            if (response == null || response.getId() == null || response.getId().isBlank()) {
                state.retry(claim, "Payment provider returned no customer ID");
                return;
            }
            state.succeeded(claim, response.getId());
        } catch (AsaasApiException exception) {
            final HttpStatus status = exception.getHttpStatus();
            if (status.is4xxClientError()
                    && status != HttpStatus.REQUEST_TIMEOUT
                    && status != HttpStatus.TOO_MANY_REQUESTS) {
                state.failed(claim, "Payment provider rejected customer details");
            } else {
                state.retry(claim, "Payment provider temporarily unavailable");
            }
        } catch (Exception exception) {
            state.retry(claim, "Payment provider outcome needs reconciliation");
        }
    }
}
