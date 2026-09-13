package com.saas.directory.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Local notification fixture. A real mail adapter can implement
 * {@link PasswordResetNotificationSender}; keeping the default fixture makes
 * the complete flow verifiable without sending credentials to a third party.
 */
@Service
public class InMemoryPasswordResetNotificationSender implements PasswordResetNotificationSender {
    private static final int MAX_NOTIFICATIONS = 100;

    private final Deque<PasswordResetNotification> delivered = new ArrayDeque<>();

    @Override
    public synchronized void send(PasswordResetNotification notification) {
        if (delivered.size() == MAX_NOTIFICATIONS) {
            delivered.removeFirst();
        }
        delivered.addLast(notification);
    }

    public synchronized List<PasswordResetNotification> deliveredNotifications() {
        return List.copyOf(new ArrayList<>(delivered));
    }
}
