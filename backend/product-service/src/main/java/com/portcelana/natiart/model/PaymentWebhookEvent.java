package com.portcelana.natiart.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Durable deduplication record for authenticated provider webhook deliveries. */
@Entity
@Table(
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_payment_webhook_provider_event", columnNames = "provider_event_id"))
public class PaymentWebhookEvent {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String paymentId;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    protected PaymentWebhookEvent() {}

    public PaymentWebhookEvent(String providerEventId, String eventType, String paymentId) {
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.paymentId = paymentId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
