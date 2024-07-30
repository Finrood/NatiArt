package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.Order;
import com.portcelana.natiart.model.OrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderItemDto {
    private String id;
    private String orderId;
    private String productId;
    private Integer quantity;
    private BigDecimal price;

    public static OrderItemDto from(OrderItem orderItem) {
        return new OrderItemDto()
                .setId(orderItem.getId())
                .setOrderId(orderItem.getOrder().getId())
                .setProductId(orderItem.getProduct().getId())
                .setQuantity(orderItem.getQuantity())
                .setPrice(orderItem.getPrice());
    }

    public OrderItemDto() {
    }

    public String getId() {
        return id;
    }

    public OrderItemDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderItemDto setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getProductId() {
        return productId;
    }

    public OrderItemDto setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public OrderItemDto setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OrderItemDto setPrice(BigDecimal price) {
        this.price = price;
        return this;
    }
}
