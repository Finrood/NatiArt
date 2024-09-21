package com.portcelana.natiart.dto.shipping;

import java.math.BigDecimal;

public class ShippingEstimate {
    private String service;
    private BigDecimal price;
    private int estimatedDeliveryDays;

    public String getService() {
        return service;
    }

    public ShippingEstimate setService(String service) {
        this.service = service;
        return this;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ShippingEstimate setPrice(BigDecimal price) {
        this.price = price;
        return this;
    }

    public int getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public ShippingEstimate setEstimatedDeliveryDays(int estimatedDeliveryDays) {
        this.estimatedDeliveryDays = estimatedDeliveryDays;
        return this;
    }
}
