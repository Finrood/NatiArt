package com.portcelana.natiart.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
public class CustomerOrderItem {
    @Id
    private final String id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", referencedColumnName = "id", nullable = false)
    private CustomerOrder customerOrder;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", referencedColumnName = "id", nullable = false)
    private Product product;

    // Immutable purchase snapshot. The product relation is retained for
    // compatibility, but catalog edits or retirement must not rewrite history.
    @Column(length = 255)
    private String productLabel;

    @Column(length = 255)
    private String productSku;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private Personalization personalization;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    public CustomerOrderItem() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public CustomerOrder getCustomerOrder() {
        return customerOrder;
    }

    public CustomerOrderItem setCustomerOrder(CustomerOrder customerOrder) {
        this.customerOrder = customerOrder;
        return this;
    }

    public Product getProduct() {
        return product;
    }

    public CustomerOrderItem setProduct(Product product) {
        this.product = product;
        return this;
    }

    public String getProductLabel() {
        return productLabel;
    }

    public CustomerOrderItem setProductLabel(String productLabel) {
        this.productLabel = productLabel;
        return this;
    }

    public String getProductSku() {
        return productSku;
    }

    public CustomerOrderItem setProductSku(String productSku) {
        this.productSku = productSku;
        return this;
    }

    public Personalization getPersonalization() {
        return personalization;
    }

    public CustomerOrderItem setPersonalization(Personalization personalization) {
        this.personalization = personalization;
        return this;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public CustomerOrderItem setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public CustomerOrderItem setPrice(BigDecimal price) {
        this.price = price;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerOrderItem customerOrderItem = (CustomerOrderItem) o;
        return Objects.equals(id, customerOrderItem.id);
    }
}
