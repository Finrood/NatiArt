package com.portcelana.natiart.dto.shipping;

public class ShippingEstimateRequest {
    private final String to;
    private final float weight; // in KG
    private final float length; // in CM
    private final float width; // in CM
    private final float height; // in CM
    private final int quantity;

    public ShippingEstimateRequest(String to, float weight, float length, float width, float height, int quantity) {
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
