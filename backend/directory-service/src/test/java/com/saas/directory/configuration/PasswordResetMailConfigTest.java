package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.saas.directory.service.InMemoryPasswordResetNotificationSender;
import com.saas.directory.service.PasswordResetNotificationSender;
import com.saas.directory.service.SmtpPasswordResetNotificationSender;

class PasswordResetMailConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PasswordResetMailConfig.class, InMemoryPasswordResetNotificationSender.class);

    private ApplicationContextRunner configured() {
        return context.withPropertyValues(
                "saas.security.password-reset.smtp.host=smtp.example.test",
                "saas.security.password-reset.smtp.username=fixture",
                "saas.security.password-reset.smtp.password=inert-fixture-password",
                "saas.security.password-reset.smtp.from=recovery@example.test",
                "saas.security.password-reset.frontend-url=https://shop.example.test/reset-password");
    }

    @Test
    void productionFailsWithoutDeliveryConfigurationAndCannotFallBackToMemory() {
        context.run(application -> assertNotNull(application.getStartupFailure()));
        context.withPropertyValues("spring.profiles.active=production,test")
                .run(application -> assertNotNull(application.getStartupFailure()));
    }

    @Test
    void configuredProductionUsesSmtpWithRequiredVerifiedTlsAuthenticationAndBoundedTimeouts() {
        configured().run(application -> {
            assertNull(application.getStartupFailure());
            assertInstanceOf(
                    SmtpPasswordResetNotificationSender.class,
                    application.getBean(PasswordResetNotificationSender.class));
            assertTrue(application
                    .getBeansOfType(InMemoryPasswordResetNotificationSender.class)
                    .isEmpty());
            final JavaMailSenderImpl sender = (JavaMailSenderImpl) application.getBean(JavaMailSender.class);
            assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
            assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
            assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.auth"));
            for (String name : new String[] {"connectiontimeout", "timeout", "writetimeout"}) {
                assertEquals("5000", sender.getJavaMailProperties().getProperty("mail.smtp." + name));
            }
            assertEquals("false", sender.getJavaMailProperties().getProperty("mail.debug"));
        });
    }

    @Test
    void productionRejectsInsecureResetUrlsAndMailHeaderInjection() {
        configured()
                .withPropertyValues("saas.security.password-reset.frontend-url=http://shop.example.test/reset-password")
                .run(application -> assertNotNull(application.getStartupFailure()));
        configured()
                .withPropertyValues(
                        "saas.security.password-reset.smtp.from=fixture@example.test\r\nBcc: recipient@example.test")
                .run(application -> assertNotNull(application.getStartupFailure()));
    }

    @Test
    void localFixtureIsExplicitAndNotificationStringsCannotExposeSecrets() {
        context.withPropertyValues("spring.profiles.active=local-h2").run(application -> {
            assertNull(application.getStartupFailure());
            assertInstanceOf(
                    InMemoryPasswordResetNotificationSender.class,
                    application.getBean(PasswordResetNotificationSender.class));
        });
        assertFalse(new com.saas.directory.service.PasswordResetNotification(
                        "private@example.test", "https://example.test/#secret")
                .toString()
                .contains("secret"));
    }
}
