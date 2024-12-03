package com.portcelana.natiart.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * This cart only applies to logged-in customers. Customers that are not logged-in will have their cart saved in their browser's cookie
 */
@Entity
public class CartItem {
    @Id
    private String id;

    @Column(nullable = false)
    private String username;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    @Column(nullable = false)
    private int quantity;

    protected CartItem() {
        // FOR JPA
    }

    public CartItem(String username, Product product) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.product = product;
        this.quantity = 1;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public CartItem setUsername(String username) {
        this.username = username;
        return this;
    }

    public Product getProduct() {
        return product;
    }

    public CartItem setProduct(Product product) {
        this.product = product;
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getQuantity() {
        return quantity;
    }

    public CartItem increaseQuantity() {
        this.quantity = this.quantity + 1;
        return this;
    }

    public CartItem decreaseQuantity() {
        this.quantity = this.quantity - 1;
        return this;
    }
}
