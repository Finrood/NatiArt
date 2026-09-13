package com.portcelana.natiart.dto.shipping;

import com.portcelana.natiart.service.support.DomainValidation;

public class ShippingEstimateRequest {
    private final String to;
    private final float weight; // in KG
    private final float length; // in CM
    private final float width; // in CM
    private final float height; // in CM
    private final int quantity;

    public ShippingEstimateRequest(String to, float weight, float length, float width, float height, int quantity) {
        this.to = DomainValidation.cep(to);
        DomainValidation.finitePositive(weight, "Shipping weight", 1000);
        DomainValidation.finitePositive(length, "Shipping length", 1000);
        DomainValidation.finitePositive(width, "Shipping width", 1000);
        DomainValidation.finitePositive(height, "Shipping height", 1000);
        if (quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Shipping quantity must be between 1 and 100");
        }
        this.weight = weight;
        this.length = length;
        this.width = width;
        this.height = height;
        this.quantity = quantity;
    }

    public String getTo() {
        return to;
    }

    public float getWeight() {
        return weight;
    }

    public float getLength() {
        return length;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public int getQuantity() {
        return quantity;
    }
}
