package com.portcelana.natiart.model;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ShippingQuoteItem {
    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private long productVersion;

    protected ShippingQuoteItem() {}

    public ShippingQuoteItem(String productId, int quantity, BigDecimal unitPrice, long productVersion) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.productVersion = productVersion;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public long getProductVersion() {
        return productVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ShippingQuoteItem that = (ShippingQuoteItem) o;
        return quantity == that.quantity
                && productVersion == that.productVersion
                && Objects.equals(productId, that.productId)
                && Objects.equals(unitPrice, that.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity, unitPrice, productVersion);
    }
}
