package com.portcelana.natiart.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.*;

import org.springframework.data.domain.AbstractAggregateRoot;

import com.portcelana.natiart.event.OrderMilestoneEvent;
import com.portcelana.natiart.model.support.OrderStatus;

@Entity
@Table(
        indexes = {
            @Index(name = "ix_order_account_owner", columnList = "accountOwnerId"),
            @Index(name = "ix_order_guest_email_date", columnList = "email,orderDate")
        },
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_customer_order_owner_idempotency",
                        columnNames = {"owner_external_id", "idempotency_key"}))
public class CustomerOrder extends AbstractAggregateRoot<CustomerOrder> {
    private Instant reservationNextAttemptAt;

    @Id
    private String id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String firstname;

    @Column(nullable = false)
    private String lastname;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String country;

    private String state;

    private String city;

    private String neighborhood;

    private String zipCode;

    private String street;

    private String houseNumber;

    private String complement;

    @Column(nullable = false)
    private Instant orderDate;

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderItem> items = new ArrayList<>();

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal deliveryAmount;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(length = 36)
    private String shippingQuoteId;

    @Column(length = 64)
    private String shippingServiceId;

    @Column(length = 8)
    private String shippingDestinationPostalCode;

    private Instant shippingQuoteExpiresAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant paidAt;
    private Instant processingAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant cancelledAt;

    @Column(length = 100)
    private String trackingCode;

    @Column(length = 500)
    private String trackingUrl;

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getProcessingAt() {
        return processingAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    /** Captures carrier details only through the validated fulfillment command. */
    public CustomerOrder setShipment(String code, String url) {
        trackingCode = code;
        trackingUrl = url;
        return this;
    }

    @Column(nullable = false)
    private String ownerExternalId;

    @Column(length = 36)
    private String guestCustomerId;

    @Column(length = 36)
    private String accountOwnerId;

    public String getGuestCustomerId() {
        return guestCustomerId;
    }

    public CustomerOrder setGuestCustomerId(String value) {
        guestCustomerId = value;
        return this;
    }

    public String getAccountOwnerId() {
        return accountOwnerId;
    }

    public CustomerOrder setAccountOwnerId(String value) {
        accountOwnerId = value;
        return this;
    }

    @Column(length = 64)
    private String idempotencyKey;

    @Column(length = 64)
    private String requestFingerprint;

    public CustomerOrder() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public CustomerOrder setVersion(Long version) {
        this.version = version;
        return this;
    }

    public String getFirstname() {
        return firstname;
    }

    public CustomerOrder setFirstname(String firstname) {
        this.firstname = firstname;
        return this;
    }

    public String getLastname() {
        return lastname;
    }

    public CustomerOrder setLastname(String lastname) {
        this.lastname = lastname;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public CustomerOrder setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public CustomerOrder setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getCountry() {
        return country;
    }

    public CustomerOrder setCountry(String country) {
        this.country = country;
        return this;
    }

    public String getState() {
        return state;
    }

    public CustomerOrder setState(String state) {
        this.state = state;
        return this;
    }

    public String getCity() {
        return city;
    }

    public CustomerOrder setCity(String city) {
        this.city = city;
        return this;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public CustomerOrder setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
        return this;
    }

    public String getZipCode() {
        return zipCode;
    }

    public CustomerOrder setZipCode(String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    public String getStreet() {
        return street;
    }

    public CustomerOrder setStreet(String street) {
        this.street = street;
        return this;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public CustomerOrder setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
        return this;
    }

    public String getComplement() {
        return complement;
    }

    public CustomerOrder setComplement(String complement) {
        this.complement = complement;
        return this;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public CustomerOrder setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
        return this;
    }

    public List<CustomerOrderItem> getItems() {
        return items;
    }

    public CustomerOrder setItems(List<CustomerOrderItem> items) {
        this.items = items;
        return this;
    }

    public BigDecimal getDeliveryAmount() {
        return deliveryAmount;
    }

    public CustomerOrder setDeliveryAmount(BigDecimal deliveryAmount) {
        this.deliveryAmount = deliveryAmount;
        return this;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public CustomerOrder setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public String getShippingQuoteId() {
        return shippingQuoteId;
    }

    public CustomerOrder setShippingQuoteId(String shippingQuoteId) {
        this.shippingQuoteId = shippingQuoteId;
        return this;
    }

    public String getShippingServiceId() {
        return shippingServiceId;
    }

    public CustomerOrder setShippingServiceId(String shippingServiceId) {
        this.shippingServiceId = shippingServiceId;
        return this;
    }

    public String getShippingDestinationPostalCode() {
        return shippingDestinationPostalCode;
    }

    public CustomerOrder setShippingDestinationPostalCode(String shippingDestinationPostalCode) {
        this.shippingDestinationPostalCode = shippingDestinationPostalCode;
        return this;
    }

    public Instant getShippingQuoteExpiresAt() {
        return shippingQuoteExpiresAt;
    }

    public CustomerOrder setShippingQuoteExpiresAt(Instant shippingQuoteExpiresAt) {
        this.shippingQuoteExpiresAt = shippingQuoteExpiresAt;
        return this;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public CustomerOrder setStatus(OrderStatus status) {
        if (this.status == status) return this;
        this.status = status;
        final Instant now = Instant.now();
        switch (status) {
            case PAID -> paidAt = now;
            case PROCESSING -> processingAt = now;
            case SHIPPED -> shippedAt = now;
            case DELIVERED -> deliveredAt = now;
            case CANCELLED -> cancelledAt = now;
            case PENDING -> {}
        }
        registerEvent(new OrderMilestoneEvent(this, status));
        return this;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }

    public CustomerOrder setOwnerExternalId(String ownerExternalId) {
        this.ownerExternalId = ownerExternalId;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public CustomerOrder setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public CustomerOrder setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
        return this;
    }

    public void addOrderItem(CustomerOrderItem item) {
        items.add(item);
        item.setCustomerOrder(this);
    }

    public void removeOrderItem(CustomerOrderItem item) {
        items.remove(item);
        item.setCustomerOrder(null);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CustomerOrder customerOrder = (CustomerOrder) o;
        return Objects.equals(id, customerOrder.id);
    }
}
