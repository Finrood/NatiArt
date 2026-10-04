package com.saas.directory.configuration;

import java.net.URI;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.saas.directory.service.PasswordResetNotificationSender;
import com.saas.directory.service.SmtpPasswordResetNotificationSender;

@Configuration
@Profile("production | (!local-h2 & !test)")
public class PasswordResetMailConfig {
    @Bean
    public JavaMailSender passwordResetMailSender(Environment environment) {
        final String host = required(environment, "saas.security.password-reset.smtp.host");
        final String username = required(environment, "saas.security.password-reset.smtp.username");
        final String password = required(environment, "saas.security.password-reset.smtp.password");
        final String from = required(environment, "saas.security.password-reset.smtp.from");
        final String frontend = required(environment, "saas.security.password-reset.frontend-url");
        final URI uri = URI.create(frontend);
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !"/reset-password".equals(uri.getPath())) {
            throw new IllegalArgumentException("Password recovery requires an HTTPS storefront reset URL");
        }
        if (!host.matches("[A-Za-z0-9.-]+") || !from.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException("Password recovery mail host/from configuration is invalid");
        }
        final int port = environment.getProperty("saas.security.password-reset.smtp.port", Integer.class, 587);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Password recovery SMTP port is invalid");
        }
        final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        final Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.debug", "false");
        return sender;
    }

    @Bean
    public PasswordResetNotificationSender passwordResetNotificationSender(
            Environment environment, JavaMailSender mailSender) {
        return new SmtpPasswordResetNotificationSender(
                mailSender, required(environment, "saas.security.password-reset.smtp.from"));
    }

    private static String required(Environment environment, String key) {
        final String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required password recovery configuration is missing: " + key);
        }
        return value;
    }
}
