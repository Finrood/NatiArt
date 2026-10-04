package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.saas.directory.dto.UserDto;
import com.saas.directory.service.AsaasProvisioningStateService.Claim;

class AsaasCustomerSearchHttpTest {
    private static final String URL = "https://provider.example.test/customers";
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(http).build();
    private final AsaasUserManager provider = new AsaasUserManager("inert-key", URL, http);
    private final AsaasProvisioningStateService state = mock(AsaasProvisioningStateService.class);
    private final Claim claim = new Claim(
            "job",
            1,
            "user-1",
            "fixture@example.test",
            new UserDto()
                    .setId("user-1")
                    .setUsername("fixture@example.test")
                    .setProfile(new com.saas.directory.dto.ProfileDto()
                            .setFirstname("Fixture")
                            .setLastname("Customer")
                            .setCpf("12345678909")));

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "{}",
                "null",
                "{\"data\":null,\"hasMore\":false}",
                "{\"data\":[],\"hasMore\":null}",
                "{\"data\":{},\"hasMore\":false}",
                "{\"data\":[null],\"hasMore\":false}",
                "{\"data\":[{}],\"hasMore\":false}",
                "{\"data\":[],\"hasMore\":true}"
            })
    void ambiguousSearchCannotPostOrCompleteAndRemainsRetryable(String body) {
        expectSearch(0, body);
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        new AsaasProvisioningService(state, provider).provisionUser(claim.username());
        verify(state).retry(claim, "Payment provider outcome needs reconciliation");
        verify(state, never()).succeeded(any(), any());
        verify(state, never()).failed(any(), any());
        server.verify(); // Only GET was allowed; any customer POST fails this fixture.
    }

    @Test
    void validEmptySearchAuthorizesExactlyOnePost() {
        expectSearch(0, "{\"data\":[],\"hasMore\":false}");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(customer("cus-created", "user-1"), MediaType.APPLICATION_JSON));
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        new AsaasProvisioningService(state, provider).provisionUser(claim.username());
        verify(state).succeeded(claim, "cus-created");
        server.verify();
    }

    @Test
    void realCustomerJsonReconcilesWithoutPost() {
        expectSearch(0, "{\"data\":[" + customer("cus-existing", "user-1") + "],\"hasMore\":false}");
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        new AsaasProvisioningService(state, provider).provisionUser(claim.username());
        verify(state).succeeded(claim, "cus-existing");
        server.verify();
    }

    @Test
    void duplicateMatchesAcrossPagesEscalateWithoutPost() {
        expectSearch(0, "{\"data\":[" + customer("cus-first", "user-1") + "],\"hasMore\":true}");
        expectSearch(100, "{\"data\":[" + customer("cus-second", "user-1") + "],\"hasMore\":false}");
        when(state.claimForUsername(claim.username())).thenReturn(claim);
        new AsaasProvisioningService(state, provider).provisionUser(claim.username());
        verify(state).failed(claim, "Multiple provider customers require manual reconciliation");
        verify(state, never()).succeeded(any(), any());
        server.verify();
    }

    @Test
    void invalidOrForeignIdentityIsAmbiguous() {
        for (final String entry : new String[] {customer("", "user-1"), customer("cus-other", "other-user")}) {
            server.reset();
            expectSearch(0, "{\"data\":[" + entry + "],\"hasMore\":false}");
            assertThrows(Exception.class, () -> provider.findCustomersByExternalReference("user-1"));
            server.verify();
        }
    }

    private void expectSearch(int offset, String body) {
        server.expect(requestTo(URL + "?externalReference=user-1&limit=100&offset=" + offset))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "inert-key"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String customer(String id, String reference) {
        return """
                {"id":"%s","externalReference":"%s","deleted":false,
                 "notificationDisabled":false,"canDelete":true,"canEdit":true,"city":0}
                """.formatted(id, reference);
    }
}
