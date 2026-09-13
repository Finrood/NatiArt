package com.portcelana.natiart.dto.shipping;

public class ShippingQuoteItemRequest {
    private String productId;
    private Integer quantity;

    public String getProductId() {
        return productId;
    }

    public ShippingQuoteItemRequest setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public ShippingQuoteItemRequest setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }
}
