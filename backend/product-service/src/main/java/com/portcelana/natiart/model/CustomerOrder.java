package com.portcelana.natiart.model;

import com.portcelana.natiart.model.support.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
public class CustomerOrder {
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

    private String complement;

    @Column(nullable = false)
    private Instant orderDate;

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderItem> items = new ArrayList<>();

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal deliveryAmount;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

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

    public OrderStatus getStatus() {
        return status;
    }

    public CustomerOrder setStatus(OrderStatus status) {
        this.status = status;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CustomerOrder customerOrder = (CustomerOrder) o;
        return Objects.equals(id, customerOrder.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
