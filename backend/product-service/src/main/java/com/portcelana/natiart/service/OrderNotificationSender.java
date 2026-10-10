package com.portcelana.natiart.service;

public interface OrderNotificationSender {
    /** Sends one immutable purchase update; a provider acknowledgement may still require retry after a crash. */
    void send(OrderMailMessage message);
}
