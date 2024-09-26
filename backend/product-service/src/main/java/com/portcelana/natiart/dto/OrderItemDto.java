package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.CustomerOrderItem;

import java.math.BigDecimal;

public class OrderItemDto {
    private String id;
    private String orderId;
    private String productId;
    private Integer quantity;
    private BigDecimal price;

    public OrderItemDto() {
    }

    public static OrderItemDto from(CustomerOrderItem customerOrderItem) {
        return new OrderItemDto()
                .setId(customerOrderItem.getId())
                .setOrderId(customerOrderItem.getCustomerOrder().getId())
                .setProductId(customerOrderItem.getProduct().getId())
                .setQuantity(customerOrderItem.getQuantity())
                .setPrice(customerOrderItem.getPrice());
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
