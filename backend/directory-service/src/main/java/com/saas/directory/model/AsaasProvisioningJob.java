package com.saas.directory.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import com.saas.directory.model.helper.PaymentProcessor;

@Entity
@Table(
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"user_id", "payment_processor"}),
            @UniqueConstraint(columnNames = {"guest_customer_id", "payment_processor"})
        })
public class AsaasProvisioningJob {
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Id
    private String id;

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "guest_customer_id")
    private GuestCustomer guestCustomer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProcessor paymentProcessor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AsaasProvisioningStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(length = 512)
    private String lastError;

    @Column(length = 128)
    private String providerCustomerId;

    @Column(length = 64)
    private String correlationId;

    protected AsaasProvisioningJob() {
        // FOR JPA
    }

    public AsaasProvisioningJob(User user, PaymentProcessor paymentProcessor, Instant nextAttemptAt) {
        this(user, paymentProcessor, nextAttemptAt, UUID.randomUUID().toString());
    }

    public AsaasProvisioningJob(
            User user, PaymentProcessor paymentProcessor, Instant nextAttemptAt, String correlationId) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.paymentProcessor = paymentProcessor;
        this.status = AsaasProvisioningStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.correlationId = correlationId;
        ensureCorrelationId();
    }

    public AsaasProvisioningJob(GuestCustomer customer, PaymentProcessor processor, Instant nextAttemptAt) {
        this.id = UUID.randomUUID().toString();
        this.guestCustomer = customer;
        this.paymentProcessor = processor;
        this.status = AsaasProvisioningStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.correlationId = UUID.randomUUID().toString();
    }

    public GuestCustomer getGuestCustomer() {
        return guestCustomer;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public PaymentProcessor getPaymentProcessor() {
        return paymentProcessor;
    }

    public AsaasProvisioningStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getProviderCustomerId() {
        return providerCustomerId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String ensureCorrelationId() {
        if (correlationId == null || !SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    public void claim(Instant now, Instant leaseUntil) {
        this.status = AsaasProvisioningStatus.IN_PROGRESS;
        this.attemptCount++;
        this.nextAttemptAt = leaseUntil;
        this.lastError = null;
    }

    public void retryAt(Instant nextAttempt, String error) {
        this.status = AsaasProvisioningStatus.PENDING;
        this.nextAttemptAt = nextAttempt;
        this.lastError = boundedError(error);
    }

    public void markSucceeded(String providerCustomerId) {
        this.status = AsaasProvisioningStatus.SUCCEEDED;
        this.providerCustomerId = providerCustomerId;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = AsaasProvisioningStatus.FAILED;
        this.lastError = boundedError(error);
    }

    private String boundedError(String error) {
        if (error == null) {
            return "Unknown provisioning failure";
        }
        return error.length() <= 512 ? error : error.substring(0, 512);
    }
}
