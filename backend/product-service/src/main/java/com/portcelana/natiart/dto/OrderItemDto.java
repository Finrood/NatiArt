package com.portcelana.natiart.dto;

import java.math.BigDecimal;

import com.portcelana.natiart.model.CustomerOrderItem;

public class OrderItemDto {
    private String id;
    private String orderId;
    private String productId;
    private String productLabel;
    private String productSku;
    private Integer quantity;
    private BigDecimal price;
    private PersonalizationDto personalizationDto;

    public OrderItemDto() {}

    public static OrderItemDto from(CustomerOrderItem customerOrderItem) {
        return new OrderItemDto()
                .setId(customerOrderItem.getId())
                .setOrderId(customerOrderItem.getCustomerOrder().getId())
                .setProductId(customerOrderItem.getProduct().getId())
                .setProductLabel(
                        customerOrderItem.getProductLabel() != null
                                ? customerOrderItem.getProductLabel()
                                : customerOrderItem.getProduct().getLabel())
                .setProductSku(
                        customerOrderItem.getProductSku() != null
                                ? customerOrderItem.getProductSku()
                                : customerOrderItem.getProduct().getId())
                .setQuantity(customerOrderItem.getQuantity())
                .setPrice(customerOrderItem.getPrice())
                .setPersonalizationDto(PersonalizationDto.from(customerOrderItem.getPersonalization()));
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

    public String getProductLabel() {
        return productLabel;
    }

    public OrderItemDto setProductLabel(String productLabel) {
        this.productLabel = productLabel;
        return this;
    }

    public String getProductSku() {
        return productSku;
    }

    public OrderItemDto setProductSku(String productSku) {
        this.productSku = productSku;
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

    public PersonalizationDto getPersonalizationDto() {
        return personalizationDto;
    }

    public OrderItemDto setPersonalizationDto(PersonalizationDto personalizationDto) {
        this.personalizationDto = personalizationDto;
        return this;
    }
}
