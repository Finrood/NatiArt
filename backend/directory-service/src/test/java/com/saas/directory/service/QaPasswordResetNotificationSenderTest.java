package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

public class QaPasswordResetNotificationSenderTest {
    @Test
    public void send_deliversToPrivateInboxWithControlToken() {
        final RestTemplate client = new RestTemplate();
        final MockRestServiceServer server =
                MockRestServiceServer.bindTo(client).build();
        final QaPasswordResetNotificationSender sender = new QaPasswordResetNotificationSender(
                "http://fixtures:8090/notifications/password-reset", "qa-control", client);
        server.expect(requestTo("http://fixtures:8090/notifications/password-reset"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-NatiArt-QA-Token", "qa-control"))
                .andExpect(
                        content()
                                .json(
                                        "{\"recipient\":\"test@example.invalid\",\"resetLink\":\"http://localhost/en/reset-password#token=fixture\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));
        sender.send(new PasswordResetNotification(
                "test@example.invalid", "http://localhost/en/reset-password#token=fixture"));
        server.verify();
    }

    @Test
    public void send_propagatesInboxFailureSoResetTransactionCanRollBack() {
        final RestTemplate client = new RestTemplate();
        final MockRestServiceServer server =
                MockRestServiceServer.bindTo(client).build();
        final QaPasswordResetNotificationSender sender =
                new QaPasswordResetNotificationSender("http://fixtures:8090/inbox", "qa-control", client);
        server.expect(requestTo("http://fixtures:8090/inbox")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(
                MailSendException.class,
                () -> sender.send(new PasswordResetNotification("test@example.invalid", "http://localhost/#fixture")));
        server.verify();
    }

    @Test
    public void constructor_rejectsMissingOrCredentialBearingConfiguration() {
        final RestTemplate client = new RestTemplate();
        assertThrows(
                IllegalArgumentException.class, () -> new QaPasswordResetNotificationSender("", "qa-control", client));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QaPasswordResetNotificationSender(
                        "http://user:password@fixtures:8090/inbox", "qa-control", client));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QaPasswordResetNotificationSender("http://fixtures:8090/inbox", "", client));
    }
}
