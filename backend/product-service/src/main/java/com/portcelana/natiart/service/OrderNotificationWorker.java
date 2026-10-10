package com.portcelana.natiart.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderNotificationWorker.class);
    private final OrderNotificationManager jobs;
    private final OrderNotificationSender sender;

    public OrderNotificationWorker(OrderNotificationManager jobs, OrderNotificationSender sender) {
        this.jobs = jobs;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${natiart.notifications.delay-millis:10000}")
    public void deliver() {
        for (String id : jobs.due()) {
            final OrderMailMessage message = jobs.claim(id);
            if (message == null) continue;
            boolean delivered = false;
            try {
                sender.send(message);
                delivered = true;
            } catch (RuntimeException failure) {
                LOGGER.warn("Purchase notification delivery failed; retry is recorded");
            }
            jobs.complete(message, delivered);
        }
    }

    @Scheduled(fixedDelay = 86400000)
    public void cleanup() {
        jobs.cleanup();
    }
}
