package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.portcelana.natiart.service.SmtpOrderNotificationSender;

class OrderMailConfigTest {
    @Test
    void productionRequiresHttpsOriginAndAllMailCredentials() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        final OrderMailConfig config = new OrderMailConfig();
        assertThrows(IllegalArgumentException.class, () -> config.orderNotificationSender(environment));
        environment.withProperty("natiart.public-url", "http://shop.example.test");
        assertThrows(IllegalArgumentException.class, () -> config.orderNotificationSender(environment));
        environment.withProperty("natiart.public-url", "https://shop.example.test");
        environment.withProperty("natiart.notifications.smtp.host", "smtp.example.test");
        environment.withProperty("natiart.notifications.smtp.username", "atelier");
        environment.withProperty("natiart.notifications.smtp.password", "test-only");
        environment.withProperty("natiart.notifications.smtp.from", "atelier@example.test");
        assertInstanceOf(SmtpOrderNotificationSender.class, config.orderNotificationSender(environment));
        environment.withProperty("natiart.notifications.smtp.from", "bad\r\nBcc: other@example.test");
        assertThrows(IllegalArgumentException.class, () -> config.orderNotificationSender(environment));
    }

    @Test
    void publicOriginRejectsCredentialsPathsAndFragments() {
        for (String origin : java.util.List.of(
                "https://user:secret@shop.test",
                "https://shop.test/orders",
                "https://shop.test#token",
                "javascript:alert(1)")) {
            assertThrows(IllegalArgumentException.class, () -> OrderMailConfig.validateOrigin(origin, true));
        }
    }
}
