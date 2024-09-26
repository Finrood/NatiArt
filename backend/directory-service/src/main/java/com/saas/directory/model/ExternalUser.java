package com.saas.directory.model;

import com.saas.directory.model.helper.PaymentProcessor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.UUID;

@Entity
public class ExternalUser {
    @Id
    private String id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProcessor paymentProcessor;
    @Column(nullable = false)
    private String externalId;

    protected ExternalUser() {
        // FOR JPA
    }

    public ExternalUser(User user, PaymentProcessor paymentProcessor, String externalId) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.paymentProcessor = paymentProcessor;
        this.externalId = externalId;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ExternalUser setUser(User user) {
        this.user = user;
        return this;
    }

    public PaymentProcessor getPaymentProcessor() {
        return paymentProcessor;
    }

    public ExternalUser setPaymentProcessor(PaymentProcessor paymentProcessor) {
        this.paymentProcessor = paymentProcessor;
        return this;
    }

    public String getExternalId() {
        return externalId;
    }

    public ExternalUser setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
