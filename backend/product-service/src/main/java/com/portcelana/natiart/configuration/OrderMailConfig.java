package com.portcelana.natiart.configuration;

import java.net.URI;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.portcelana.natiart.service.*;

@Configuration
public class OrderMailConfig {
    @Bean
    public OrderNotificationSender orderNotificationSender(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("production"))) {
            final String publicUrl = required(environment, "natiart.public-url");
            validateOrigin(publicUrl, true);
            final String host = required(environment, "natiart.notifications.smtp.host");
            final String from = required(environment, "natiart.notifications.smtp.from");
            if (!host.matches("[A-Za-z0-9.-]+") || !from.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+")) {
                throw new IllegalArgumentException("Order mail host/from configuration is invalid");
            }
            final JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            final int port = environment.getProperty("natiart.notifications.smtp.port", Integer.class, 587);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Order mail SMTP port is invalid");
            sender.setPort(port);
            sender.setUsername(required(environment, "natiart.notifications.smtp.username"));
            sender.setPassword(required(environment, "natiart.notifications.smtp.password"));
            final java.util.Properties properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", "true");
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
            properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            properties.setProperty("mail.smtp.connectiontimeout", "5000");
            properties.setProperty("mail.smtp.timeout", "5000");
            properties.setProperty("mail.smtp.writetimeout", "5000");
            return new SmtpOrderNotificationSender(sender, from);
        }
        if (environment.acceptsProfiles(Profiles.of("qa-h2"))) {
            validateOrigin(required(environment, "natiart.public-url"), false);
            return new QaOrderNotificationSender(
                    required(environment, "natiart.qa.order-notifications-url"),
                    required(environment, "natiart.qa.control-token"));
        }
        return message -> LoggerFactory.getLogger(OrderMailConfig.class)
                .info("Local purchase notification accepted; no external email is sent");
    }

    static void validateOrigin(String value, boolean secure) {
        final URI uri = URI.create(value);
        if (!("https".equals(uri.getScheme()) || (!secure && "http".equals(uri.getScheme())))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Purchase emails require a valid storefront origin");
        }
    }

    private static String required(Environment environment, String key) {
        final String value = environment.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Required order email configuration is missing: " + key);
        return value;
    }
}
