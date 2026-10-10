package com.portcelana.natiart.model;

import java.time.Instant;

import jakarta.persistence.*;

import com.portcelana.natiart.model.support.OrderStatus;

@Entity
@Table(indexes = @Index(name = "ix_order_notification_due", columnList = "deliveredAt,nextAttemptAt"))
public class OrderNotification {
    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus milestone;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 16000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant deliveredAt;
    private Instant supersededAt;
    private Instant leaseUntil;

    @Column(length = 36)
    private String leaseId;

    private int attempts;
    private Instant retryRequestedAt;

    protected OrderNotification() {}

    public OrderNotification(String orderId, OrderStatus milestone, String recipient, String subject, String body) {
        this.id = orderId + ":" + milestone.name();
        this.orderId = orderId;
        this.milestone = milestone;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.createdAt = Instant.now();
        this.nextAttemptAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getMilestone() {
        return milestone;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public void supersede(Instant now) {
        supersededAt = now;
        leaseId = null;
        leaseUntil = null;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getRetryRequestedAt() {
        return retryRequestedAt;
    }

    public void claim(String id, Instant now) {
        leaseId = id;
        leaseUntil = now.plusSeconds(120);
        attempts++;
    }

    public void complete(Instant now, boolean delivered) {
        if (delivered) deliveredAt = now;
        else nextAttemptAt = now.plusSeconds(Math.min(86400, 30L << Math.min(attempts, 11)));
        leaseId = null;
        leaseUntil = null;
    }

    public void retry(Instant now) {
        attempts = 0;
        nextAttemptAt = now;
        retryRequestedAt = now;
    }
}
