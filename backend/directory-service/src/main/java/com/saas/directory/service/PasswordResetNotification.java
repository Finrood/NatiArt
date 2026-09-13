package com.saas.directory.service;

/**
 * A reset notification delivered to the account address. The token lives only
 * in the fragment of the link, so it is not sent to the server in a GET URL.
 */
public record PasswordResetNotification(String recipient, String resetLink) {}
