package com.saas.directory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.saas.directory.dto.PasswordChangeDto;
import com.saas.directory.model.AsaasProvisioningJob;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.AsaasProvisioningJobRepository;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.UserRepository;
import com.saas.directory.service.AccountMutationManager;
import com.saas.directory.service.AsaasProvisioningService;
import com.saas.directory.service.AsaasUserManager;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:account-profile;DB_CLOSE_DELAY=-1",
            "spring.datasource.hikari.maximum-pool-size=1",
            "saas.security.rate-limit.max-requests-per-minute=200"
        })
class AccountProfileHttpIntegrationTest {
    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository users;

    @Autowired
    private ExternalUserRepository externalUsers;

    @Autowired
    private AsaasProvisioningJobRepository jobs;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private AccountMutationManager mutations;

    @MockitoBean
    private AsaasProvisioningService provisioning;

    @MockitoBean
    private AsaasUserManager provider;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void signupProfileEditAndPasswordChangePreserveIdentityAndRevokeEveryOldSession() throws Exception {
        final String email = "account-" + UUID.randomUUID() + "@example.test";
        final JsonNode registered = register(email);
        final JsonNode session = login(email, "OldPassword123");
        final JsonNode otherSession = login(email, "OldPassword123");
        final String access = session.get("accessToken").asText();
        final ObjectNode profile = (ObjectNode)
                json.readTree(send("GET", "/users/current", access, "").body()).get("profile");
        assertEquals("123", profile.get("houseNumber").asText());
        assertEquals("Apartment 4", profile.get("complement").asText());
        assertEquals(
                403,
                send("PUT", "/users/current/profile", null, update(profile, "OldPassword123"))
                        .statusCode());
        assertEquals(
                400,
                send("PUT", "/users/current/profile", access, update(profile, "incorrect"))
                        .statusCode());
        assertEquals(200, send("POST", "/validate-token", access, "").statusCode());

        final ObjectNode edited = profile.deepCopy();
        edited.put("id", "someone-elses-profile");
        edited.put("firstname", "  Beatriz  ");
        edited.put("houseNumber", "  456B  ");
        edited.put("complement", "Apartment 7");
        edited.put("phone", "");
        final HttpResponse<String> saved =
                send("PUT", "/users/current/profile", access, update(edited, "OldPassword123"));
        assertEquals(200, saved.statusCode(), saved.body());
        final JsonNode newProfile = json.readTree(saved.body());
        assertEquals(profile.get("id"), newProfile.get("id"));
        assertEquals("Beatriz", newProfile.get("firstname").asText());
        assertEquals("456B", newProfile.get("houseNumber").asText());
        assertTrue(newProfile.get("version").asLong() > profile.get("version").asLong());
        assertEquals(
                409,
                send("PUT", "/users/current/profile", access, update(profile, "OldPassword123"))
                        .statusCode());
        final JsonNode afterEdit =
                json.readTree(send("GET", "/users/current", access, "").body());
        assertEquals(registered.get("id"), afterEdit.get("id"));
        assertEquals("Apartment 7", afterEdit.get("profile").get("complement").asText());
        verify(provider, never()).updateCustomer(anyString(), any());

        assertEquals(
                403,
                send(
                                "POST",
                                "/users/current/change-password",
                                null,
                                "{\"currentPassword\":\"OldPassword123\",\"password\":\"NewPassword456\",\"passwordConfirmation\":\"NewPassword456\"}")
                        .statusCode());
        assertEquals(
                400,
                send(
                                "POST",
                                "/users/current/change-password",
                                access,
                                "{\"currentPassword\":\"OldPassword123\",\"password\":\"short\",\"passwordConfirmation\":\"short\"}")
                        .statusCode());
        final HttpResponse<String> changed = send(
                "POST",
                "/users/current/change-password",
                access,
                "{\"currentPassword\":\"OldPassword123\",\"password\":\"NewPassword456\",\"passwordConfirmation\":\"NewPassword456\"}");
        assertEquals(204, changed.statusCode(), changed.body());
        for (final JsonNode old : new JsonNode[] {session, otherSession}) {
            assertEquals(
                    401,
                    send("GET", "/users/current", old.get("accessToken").asText(), "")
                            .statusCode());
            assertEquals(
                    401,
                    send("POST", "/refresh-token", old.get("refreshToken").asText(), "")
                            .statusCode());
        }
        assertEquals(
                401,
                send("POST", "/login", null, credentials(email, "OldPassword123"))
                        .statusCode());
        final JsonNode nextSession = login(email, "NewPassword456");
        final JsonNode nextAccount = json.readTree(
                send("GET", "/users/current", nextSession.get("accessToken").asText(), "")
                        .body());
        assertEquals(registered.get("id"), nextAccount.get("id"));
        assertEquals(newProfile.get("id"), nextAccount.get("profile").get("id"));
        assertEquals("456B", nextAccount.get("profile").get("houseNumber").asText());
    }

    @Test
    void providerFailureAndInFlightProvisioningKeepSavedProfileIntactAndRetrySameCustomer() throws Exception {
        final String email = "provider-" + UUID.randomUUID() + "@example.test";
        register(email);
        final String access = login(email, "OldPassword123").get("accessToken").asText();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            final User user = users.findByUsernameForUpdate(email).orElseThrow();
            externalUsers.save(new ExternalUser(user, PaymentProcessor.ASAAS, "cus_existing"));
            jobs.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                    .orElseThrow()
                    .markSucceeded("cus_existing");
        });
        final ObjectNode original = (ObjectNode)
                json.readTree(send("GET", "/users/current", access, "").body()).get("profile");
        final ObjectNode edited = original.deepCopy().put("houseNumber", "999");
        doThrow(new ResourceAccessException("timeout")).when(provider).updateCustomer(eq("cus_existing"), any());
        assertEquals(
                503,
                send("PUT", "/users/current/profile", access, update(edited, "OldPassword123"))
                        .statusCode());
        assertEquals(
                "123",
                json.readTree(send("GET", "/users/current", access, "").body())
                        .get("profile")
                        .get("houseNumber")
                        .asText());
        doNothing().when(provider).updateCustomer(eq("cus_existing"), any());
        assertEquals(
                200,
                send("PUT", "/users/current/profile", access, update(edited, "OldPassword123"))
                        .statusCode());
        verify(provider, times(2))
                .updateCustomer(
                        eq("cus_existing"),
                        argThat(value -> value.addressNumber().equals("999") && value.externalReference() != null));
        assertEquals(
                1,
                externalUsers.findAll().stream()
                        .filter(value -> value.getUser().getUsername().equals(email))
                        .count());

        final String pendingEmail = "pending-" + UUID.randomUUID() + "@example.test";
        register(pendingEmail);
        final String pendingAccess =
                login(pendingEmail, "OldPassword123").get("accessToken").asText();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            final User user = users.findByUsernameForUpdate(pendingEmail).orElseThrow();
            final AsaasProvisioningJob job = jobs.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                    .orElseThrow();
            job.claim(Instant.now(), Instant.now().plusSeconds(120));
        });
        final ObjectNode pendingProfile = (ObjectNode)
                json.readTree(send("GET", "/users/current", pendingAccess, "").body())
                        .get("profile");
        pendingProfile.put("houseNumber", "777");
        assertEquals(
                409,
                send("PUT", "/users/current/profile", pendingAccess, update(pendingProfile, "OldPassword123"))
                        .statusCode());
        assertEquals(
                "123",
                json.readTree(send("GET", "/users/current", pendingAccess, "").body())
                        .get("profile")
                        .get("houseNumber")
                        .asText());
    }

    @Test
    void incorrectCurrentPasswordAttemptsStayThrottledDespiteTransactionRollback() throws Exception {
        final String email = "throttled-" + UUID.randomUUID() + "@example.test";
        register(email);
        final String access = login(email, "OldPassword123").get("accessToken").asText();
        final ObjectNode profile = (ObjectNode)
                json.readTree(send("GET", "/users/current", access, "").body()).get("profile");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(
                    400,
                    send("PUT", "/users/current/profile", access, update(profile, "incorrect"))
                            .statusCode());
        }
        assertEquals(
                429,
                send("PUT", "/users/current/profile", access, update(profile, "OldPassword123"))
                        .statusCode());
        assertEquals(200, send("GET", "/users/current", access, "").statusCode());
    }

    @Test
    void oldPasswordLoginAndRefreshCannotIssueTokensAcrossAnUncommittedPasswordChange() throws Exception {
        final String email = "race-" + UUID.randomUUID() + "@example.test";
        register(email);
        final JsonNode old = login(email, "OldPassword123");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            final java.util.List<Future<HttpResponse<String>>> requests = new java.util.ArrayList<>();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                mutations.changePassword(
                        email, new PasswordChangeDto("OldPassword123", "NewPassword456", "NewPassword456"));
                requests.add(executor.submit(() -> send("POST", "/login", null, credentials(email, "OldPassword123"))));
                requests.add(executor.submit(() ->
                        send("POST", "/refresh-token", old.get("refreshToken").asText(), "")));
                for (final Future<HttpResponse<String>> pending : requests) {
                    assertThrows(TimeoutException.class, () -> pending.get(150, TimeUnit.MILLISECONDS));
                }
            });
            for (final Future<HttpResponse<String>> pending : requests) {
                assertEquals(401, pending.get(10, TimeUnit.SECONDS).statusCode());
            }
        }
        assertEquals(
                200,
                send("POST", "/login", null, credentials(email, "NewPassword456"))
                        .statusCode());
    }

    private JsonNode register(String email) throws Exception {
        final String body = """
                {"username":"%s","password":"OldPassword123","profile":{"firstname":"Ana","lastname":"Silva","cpf":"123.456.789-09","phone":"","country":"Brazil","state":"sp","city":"São Paulo","neighborhood":"Centro","zipCode":"01001-000","street":"Rua Principal","houseNumber":"123","complement":"Apartment 4"}}
                """.formatted(email);
        final HttpResponse<String> response = send("POST", "/register-user", null, body);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private JsonNode login(String email, String password) throws Exception {
        final HttpResponse<String> response = send("POST", "/login", null, credentials(email, password));
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String credentials(String email, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String update(ObjectNode profile, String password) {
        final ObjectNode body = json.createObjectNode();
        body.set("profile", profile);
        body.put("currentPassword", password);
        return body.toString();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
