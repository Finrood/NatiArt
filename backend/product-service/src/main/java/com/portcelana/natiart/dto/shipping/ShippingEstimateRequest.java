package com.portcelana.natiart.dto.shipping;

public class ShippingEstimateRequest {
    private final String to;
    private final float weight; // in KG
    private final float length; // in CM
    private final float width; // in CM
    private final float height; // in CM
    private final int quantity;

    public ShippingEstimateRequest(String to, float weight, float length, float width, float height, int quantity) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Destination postal code cannot be empty");
        }
        if (weight <= 0 || length <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Shipping weight and dimensions must be greater than zero");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Shipping quantity must be at least one");
        }
        this.to = to;
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
