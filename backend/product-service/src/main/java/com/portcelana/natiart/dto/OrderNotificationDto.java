package com.portcelana.natiart.dto;

import java.time.Instant;

import com.portcelana.natiart.model.OrderNotification;
import com.portcelana.natiart.model.support.OrderStatus;

public record OrderNotificationDto(
        String id,
        String orderId,
        OrderStatus milestone,
        int attempts,
        boolean exhausted,
        Instant nextAttemptAt,
        Instant retryRequestedAt) {
    public static OrderNotificationDto from(OrderNotification job) {
        return new OrderNotificationDto(
                job.getId(),
                job.getOrderId(),
                job.getMilestone(),
                job.getAttempts(),
                job.getAttempts() >= 8,
                job.getNextAttemptAt(),
                job.getRetryRequestedAt());
    }
}
