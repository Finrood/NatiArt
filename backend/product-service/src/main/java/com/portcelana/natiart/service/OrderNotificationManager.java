package com.portcelana.natiart.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderNotificationDto;
import com.portcelana.natiart.event.OrderMilestoneEvent;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.OrderNotification;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderNotificationRepository;
import com.portcelana.natiart.repository.OrderRepository;

/** Durable purchase communications; all network delivery happens outside these transactions. */
@Service
public class OrderNotificationManager {
    private final OrderNotificationRepository jobs;
    private final OrderRepository orders;
    private String publicUrl = "http://localhost:4200";

    public OrderNotificationManager(OrderNotificationRepository jobs, OrderRepository orders) {
        this.jobs = jobs;
        this.orders = orders;
    }

    @Value("${natiart.public-url:http://localhost:4200}")
    public void setPublicUrl(String value) {
        publicUrl = value.replaceAll("/+$", "");
    }

    /** Inserts the snapshot synchronously, so it commits or rolls back with the order milestone. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(OrderMilestoneEvent event) {
        final String id = event.order().getId() + ":" + event.status().name();
        if (jobs.existsById(id)) return;
        final OrderMailContent content = OrderMailContent.from(event, publicUrl);
        jobs.save(new OrderNotification(
                event.order().getId(), event.status(), event.order().getEmail(), content.subject(), content.body()));
    }

    @Transactional(readOnly = true)
    public List<String> due() {
        return jobs.findDueIds(Instant.now(), PageRequest.of(0, 20));
    }

    /** Leases one due snapshot; obsolete notices are suppressed before any provider call. */
    @Transactional
    public OrderMailMessage claim(String id) {
        final OrderNotification job = jobs.findLocked(id).orElse(null);
        final Instant now = Instant.now();
        if (job == null
                || job.getDeliveredAt() != null
                || job.getSupersededAt() != null
                || job.getAttempts() >= 8
                || job.getNextAttemptAt().isAfter(now)
                || (job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(now))) return null;
        final CustomerOrder order = orders.findById(job.getOrderId()).orElse(null);
        if (order == null
                || (job.getMilestone() != OrderStatus.PAID
                        && job.getMilestone() != OrderStatus.DELIVERED
                        && job.getMilestone() != OrderStatus.CANCELLED
                        && order.getStatus() != job.getMilestone())) {
            job.supersede(now);
            return null;
        }
        final String lease = UUID.randomUUID().toString();
        job.claim(lease, now);
        return new OrderMailMessage(id, lease, job.getRecipient(), job.getSubject(), job.getBody());
    }

    /** Acknowledges only the current lease; a delayed worker cannot overwrite a newer attempt. */
    @Transactional
    public void complete(OrderMailMessage message, boolean delivered) {
        final OrderNotification job = jobs.findLocked(message.id()).orElse(null);
        if (job != null
                && message.leaseId().equals(job.getLeaseId())
                && job.getDeliveredAt() == null
                && job.getSupersededAt() == null) {
            job.complete(Instant.now(), delivered);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderNotificationDto> attention() {
        return jobs.findAttention(Instant.now(), PageRequest.of(0, 50)).stream()
                .map(OrderNotificationDto::from)
                .toList();
    }

    @Transactional
    public void retry(String id) {
        final OrderNotification job =
                jobs.findLocked(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        final Instant now = Instant.now();
        if (job.getDeliveredAt() != null
                || job.getSupersededAt() != null
                || (job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(now))) {
            throw new IllegalArgumentException("A delivered or active notification cannot be retried");
        }
        job.retry(now);
    }

    @Transactional
    public void cleanup() {
        jobs.deleteDeliveredBefore(Instant.now().minusSeconds(90L * 86400));
    }
}
