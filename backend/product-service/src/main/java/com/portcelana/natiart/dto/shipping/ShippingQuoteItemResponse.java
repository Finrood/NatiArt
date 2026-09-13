package com.portcelana.natiart.dto.shipping;

import java.math.BigDecimal;

import com.portcelana.natiart.model.ShippingQuoteItem;

public class ShippingQuoteItemResponse {
    private String productId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;

    public static ShippingQuoteItemResponse from(ShippingQuoteItem item) {
        return new ShippingQuoteItemResponse()
                .setProductId(item.getProductId())
                .setQuantity(item.getQuantity())
                .setUnitPrice(item.getUnitPrice())
                .setLineAmount(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    public String getProductId() {
        return productId;
    }

    public ShippingQuoteItemResponse setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public int getQuantity() {
        return quantity;
    }

    public ShippingQuoteItemResponse setQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public ShippingQuoteItemResponse setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        return this;
    }

    public BigDecimal getLineAmount() {
        return lineAmount;
    }

    public ShippingQuoteItemResponse setLineAmount(BigDecimal lineAmount) {
        this.lineAmount = lineAmount;
        return this;
    }
}
