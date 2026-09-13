package com.saas.directory.model;

import java.time.Instant;
import java.util.UUID;

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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "payment_processor"}))
public class AsaasProvisioningJob {
    @Id
    private String id;

    @Version
    private long version;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    protected AsaasProvisioningJob() {
        // FOR JPA
    }

    public AsaasProvisioningJob(User user, PaymentProcessor paymentProcessor, Instant nextAttemptAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.paymentProcessor = paymentProcessor;
        this.status = AsaasProvisioningStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
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
