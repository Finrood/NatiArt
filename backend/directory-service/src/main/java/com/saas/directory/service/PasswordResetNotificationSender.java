package com.saas.directory.service;

public interface PasswordResetNotificationSender {
    void send(PasswordResetNotification notification);
}
