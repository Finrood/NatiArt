package com.saas.directory.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Delivers recovery links to the account address through the configured mail server. */
public class SmtpPasswordResetNotificationSender implements PasswordResetNotificationSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetNotificationSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(PasswordResetNotification notification) {
        final SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipient());
        message.setSubject(
                notification.checkoutClaim() ? "Your NatiArt orders and account" : "Reset your NatiArt password");
        message.setText((notification.checkoutClaim()
                        ? "Use this link within 15 minutes to verify your email and connect your guest orders:\n\n"
                        : "Use this link within 15 minutes to reset your password:\n\n")
                + notification.resetLink()
                + "\n\nIf you did not request this, you can ignore this email.");
        mailSender.send(message);
    }
}
