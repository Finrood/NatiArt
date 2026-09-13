package com.portcelana.natiart.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipping_quote")
public class ShippingQuote {
    @Id
    private String id;

    @Column(nullable = false)
    private String ownerExternalId;

    @Column(nullable = false, length = 8)
    private String destinationPostalCode;

    @Column(nullable = false, length = 64)
    private String serviceId;

    @Column(nullable = false, length = 120)
    private String serviceName;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal itemAmount;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal shippingAmount;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shipping_quote_item", joinColumns = @JoinColumn(name = "shipping_quote_id"))
    private List<ShippingQuoteItem> items = new ArrayList<>();

    public ShippingQuote() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }

    public ShippingQuote setOwnerExternalId(String ownerExternalId) {
        this.ownerExternalId = ownerExternalId;
        return this;
    }

    public String getDestinationPostalCode() {
        return destinationPostalCode;
    }

    public ShippingQuote setDestinationPostalCode(String destinationPostalCode) {
        this.destinationPostalCode = destinationPostalCode;
        return this;
    }

    public String getServiceId() {
        return serviceId;
    }

    public ShippingQuote setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ShippingQuote setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public BigDecimal getItemAmount() {
        return itemAmount;
    }

    public ShippingQuote setItemAmount(BigDecimal itemAmount) {
        this.itemAmount = itemAmount;
        return this;
    }

    public BigDecimal getShippingAmount() {
        return shippingAmount;
    }

    public ShippingQuote setShippingAmount(BigDecimal shippingAmount) {
        this.shippingAmount = shippingAmount;
        return this;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public ShippingQuote setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public ShippingQuote setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public ShippingQuote setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
        return this;
    }

    public List<ShippingQuoteItem> getItems() {
        return items;
    }

    public ShippingQuote setItems(List<ShippingQuoteItem> items) {
        this.items = new ArrayList<>(items);
        return this;
    }

    public ShippingQuoteItem getItem(String productId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getProductId(), productId))
                .findFirst()
                .orElse(null);
    }
}
