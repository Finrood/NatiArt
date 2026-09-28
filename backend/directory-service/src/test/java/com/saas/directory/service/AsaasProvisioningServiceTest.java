package com.saas.directory.service;

import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.service.AsaasProvisioningStateService.Claim;

class AsaasProvisioningServiceTest {
    private final AsaasProvisioningStateService state = mock(AsaasProvisioningStateService.class);
    private final AsaasUserManager provider = mock(AsaasUserManager.class);
    private final AsaasProvisioningService service = new AsaasProvisioningService(state, provider);
    private final Claim claim = new Claim("job-1", 1, "user-1", "customer@example.com", new UserDto().setId("user-1"));

    @Test
    void reconcilesExistingCustomerBeforeCreatingAnother() throws Exception {
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        when(provider.findCustomersByExternalReference(claim.userId()))
                .thenReturn(List.of(customerResponse("cus_existing", claim.userId())));

        service.provisionUser(claim.username());

        verify(state).succeeded(claim, "cus_existing");
        verify(provider, never()).registerUser(any());
    }

    @Test
    void rateLimitRemainsRetryable() throws Exception {
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        when(provider.findCustomersByExternalReference(claim.userId()))
                .thenThrow(new AsaasApiException("throttled", HttpStatus.TOO_MANY_REQUESTS));

        service.provisionUser(claim.username());

        verify(state).retry(claim, "Payment provider temporarily unavailable");
        verify(state, never()).failed(any(), any());
    }

    @Test
    void terminalProviderRejectionRemainsInspectablyFailed() throws Exception {
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        when(provider.findCustomersByExternalReference(claim.userId()))
                .thenThrow(new AsaasApiException("bad customer", HttpStatus.BAD_REQUEST));

        service.provisionUser(claim.username());

        verify(state).failed(claim, "Payment provider rejected customer details");
        verify(state, never()).retry(any(), any());
    }

    @Test
    void duplicateProviderMatchesRequireManualReconciliation() throws Exception {
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        when(provider.findCustomersByExternalReference(claim.userId()))
                .thenReturn(List.of(
                        customerResponse("cus_one", claim.userId()), customerResponse("cus_two", claim.userId())));

        service.provisionUser(claim.username());

        verify(state).failed(claim, "Multiple provider customers require manual reconciliation");
        verify(provider, never()).registerUser(any());
    }

    private AsaasCustomerCreationResponse customerResponse(String id, String externalReference) {
        return new AsaasCustomerCreationResponse(
                "customer",
                id,
                "2025-01-01",
                "Test User",
                "test@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                externalReference,
                false,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                0,
                null,
                null,
                null);
    }
}
