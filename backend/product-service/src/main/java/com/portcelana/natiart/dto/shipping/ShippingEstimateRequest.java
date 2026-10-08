package com.portcelana.natiart.dto.shipping;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.portcelana.natiart.service.support.DomainValidation;
import com.portcelana.natiart.service.support.InputValidationException;

public class ShippingEstimateRequest {
    private final String to;
    private final float weight; // in KG
    private final float length; // in CM
    private final float width; // in CM
    private final float height; // in CM
    private final int quantity;
    private final BigDecimal insuranceValue;

    public ShippingEstimateRequest(String to, float weight, float length, float width, float height, int quantity) {
        this(to, weight, length, width, height, quantity, BigDecimal.ZERO);
    }

    @JsonCreator
    public ShippingEstimateRequest(
            @JsonProperty("to") String to,
            @JsonProperty("weight") float weight,
            @JsonProperty("length") float length,
            @JsonProperty("width") float width,
            @JsonProperty("height") float height,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("insuranceValue") BigDecimal insuranceValue) {
        this.to = DomainValidation.cep(to);
        DomainValidation.shippingWeight(weight);
        DomainValidation.finitePositive(length, "length", 200);
        DomainValidation.finitePositive(width, "width", 200);
        DomainValidation.finitePositive(height, "height", 200);
        if (quantity < 1 || quantity > 100) {
            throw new InputValidationException("quantity", "quantity must be between 1 and 100");
        }
        this.weight = weight;
        this.length = length;
        this.width = width;
        this.height = height;
        this.quantity = quantity;
        final BigDecimal declaredValue = insuranceValue == null ? BigDecimal.ZERO : insuranceValue;
        if (declaredValue.signum() < 0
                || declaredValue.scale() > 2
                || declaredValue.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new InputValidationException(
                    "insuranceValue", "insuranceValue must be a non-negative BRL amount with at most two decimals");
        }
        this.insuranceValue = declaredValue;
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

    public BigDecimal getInsuranceValue() {
        return insuranceValue;
    }
}
