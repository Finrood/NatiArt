package com.portcelana.natiart.service;

import java.util.stream.Collectors;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

/** Delivers accessible text and a matching minimal branded HTML version over authenticated TLS. */
public class SmtpOrderNotificationSender implements OrderNotificationSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpOrderNotificationSender(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(OrderMailMessage message) {
        final MimeMessage email = sender.createMimeMessage();
        try {
            final MimeMessageHelper helper = new MimeMessageHelper(email, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.body(), """
                <!doctype html><html><body style="margin:0;background:#faf7f1;color:#352e2b;font-family:Arial,sans-serif">
                <main style="max-width:600px;margin:24px auto;padding:32px"><h1 style="font-family:Georgia,serif;font-weight:400">NatiArt</h1>
                <div style="line-height:1.7;white-space:pre-wrap">
                """ + htmlBody(message.body()) + "</div></main></body></html>");
            sender.send(email);
        } catch (MessagingException invalid) {
            throw new IllegalStateException("Purchase email could not be prepared");
        }
    }

    private static String htmlBody(String body) {
        return body.lines()
                .map(line -> {
                    final String escaped = HtmlUtils.htmlEscape(line);
                    if (line.startsWith("https://") || line.startsWith("http://localhost:")) {
                        return "<a href=\"" + escaped + "\" style=\"color:#73463b\">" + escaped + "</a>";
                    }
                    return escaped;
                })
                .collect(Collectors.joining("<br>"));
    }
}
