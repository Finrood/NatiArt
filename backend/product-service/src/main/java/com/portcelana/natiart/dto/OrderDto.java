package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderDto {
    private String id;
    private String firstname;
    private String lastname;
    private String email;
    private String phone;
    private String country;
    private String state;
    private String city;
    private String neighborhood;
    private String zipCode;
    private String street;
    private String complement;
    private Instant orderDate;
    private List<OrderItemDto> items = new ArrayList<>();
    private BigDecimal deliveryAmount;
    private BigDecimal totalAmount;
    private OrderStatus status;

    public static OrderDto from(CustomerOrder customerOrder) {
        return new OrderDto()
                .setId(customerOrder.getId())
                .setFirstname(customerOrder.getFirstname())
                .setLastname(customerOrder.getLastname())
                .setEmail(customerOrder.getEmail())
                .setPhone(customerOrder.getPhone())
                .setCountry(customerOrder.getCountry())
                .setState(customerOrder.getState())
                .setCity(customerOrder.getCity())
                .setNeighborhood(customerOrder.getNeighborhood())
                .setZipCode(customerOrder.getZipCode())
                .setStreet(customerOrder.getStreet())
                .setComplement(customerOrder.getComplement())
                .setOrderDate(customerOrder.getOrderDate())
                .setItems(customerOrder.getItems().stream()
                        .map(OrderItemDto::from)
                        .toList())
                .setDeliveryAmount(customerOrder.getDeliveryAmount())
                .setTotalAmount(customerOrder.getTotalAmount())
                .setStatus(customerOrder.getStatus());
    }

    public OrderDto() {
    }

    public String getId() {
        return id;
    }

    public OrderDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getFirstname() {
        return firstname;
    }

    public OrderDto setFirstname(String firstname) {
        this.firstname = firstname;
        return this;
    }

    public String getLastname() {
        return lastname;
    }

    public OrderDto setLastname(String lastname) {
        this.lastname = lastname;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public OrderDto setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public OrderDto setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getCountry() {
        return country;
    }

    public OrderDto setCountry(String country) {
        this.country = country;
        return this;
    }

    public String getState() {
        return state;
    }

    public OrderDto setState(String state) {
        this.state = state;
        return this;
    }

    public String getCity() {
        return city;
    }

    public OrderDto setCity(String city) {
        this.city = city;
        return this;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public OrderDto setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
        return this;
    }

    public String getZipCode() {
        return zipCode;
    }

    public OrderDto setZipCode(String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    public String getStreet() {
        return street;
    }

    public OrderDto setStreet(String street) {
        this.street = street;
        return this;
    }

    public String getComplement() {
        return complement;
    }

    public OrderDto setComplement(String complement) {
        this.complement = complement;
        return this;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public OrderDto setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
        return this;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public OrderDto setItems(List<OrderItemDto> items) {
        this.items = items;
        return this;
    }

    public BigDecimal getDeliveryAmount() {
        return deliveryAmount;
    }

    public OrderDto setDeliveryAmount(BigDecimal deliveryAmount) {
        this.deliveryAmount = deliveryAmount;
        return this;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderDto setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OrderDto setStatus(OrderStatus status) {
        this.status = status;
        return this;
    }
}
