package com.portcelana.natiart.dto;

public class ShippingEstimateRequest {
    final private String to;
    final private float weight; // in KG
    final private float length; // in CM
    final private float width; // in CM
    final private float height; // in CM
    final private int quantity;

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
