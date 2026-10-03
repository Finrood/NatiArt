package com.saas.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.dto.UserDto;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountStateHttpIntegrationTest {
    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserAuthenticationProvider tokens;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void adminDowngradeAndDisableRevokeOldAccessAndRefreshTokens() throws Exception {
        final Role adminRole =
                roles.findRoleByLabel(RoleName.ADMIN).orElseGet(() -> roles.save(new Role(RoleName.ADMIN)));
        final Role userRole = roles.findRoleByLabel(RoleName.USER).orElseGet(() -> roles.save(new Role(RoleName.USER)));
        final User operator =
                users.save(new User("operator-account-state@example.test", "password").setRole(adminRole));
        final User target = users.save(new User("target-account-state@example.test", "password").setRole(adminRole));
        final User buyer = users.save(new User("buyer-account-state@example.test", "password").setRole(userRole));
        final String operatorToken = tokens.createAccessToken(UserDto.from(operator, null));
        final String targetToken = tokens.createAccessToken(UserDto.from(target, null));
        final String targetRefresh = tokens.createRefreshToken(UserDto.from(target, null));
        final String buyerToken = tokens.createAccessToken(UserDto.from(buyer, null));
        final String path = "/admin/users/" + target.getId() + "/account-state";

        assertEquals(200, send("POST", "/validate-token", targetToken, "").statusCode());
        assertEquals(403, send("PATCH", path, buyerToken, "{\"role\":\"USER\"}").statusCode());
        assertEquals(
                200, send("PATCH", path, operatorToken, "{\"role\":\"USER\"}").statusCode());
        assertEquals(401, send("POST", "/validate-token", targetToken, "").statusCode());
        assertEquals(401, send("POST", "/refresh-token", targetRefresh, "").statusCode());

        final HttpResponse<String> newLogin = send(
                "POST",
                "/login",
                null,
                "{\"username\":\"target-account-state@example.test\",\"password\":\"password\"}");
        assertEquals(200, newLogin.statusCode());
        final String newUserToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(newLogin.body())
                .get("accessToken")
                .asText();
        assertEquals(
                403, send("PATCH", path, newUserToken, "{\"active\":false}").statusCode());

        assertEquals(
                200, send("PATCH", path, operatorToken, "{\"active\":false}").statusCode());
        assertEquals(401, send("POST", "/validate-token", newUserToken, "").statusCode());
        assertEquals(
                401,
                send(
                                "POST",
                                "/login",
                                null,
                                "{\"username\":\"target-account-state@example.test\",\"password\":\"password\"}")
                        .statusCode());
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
