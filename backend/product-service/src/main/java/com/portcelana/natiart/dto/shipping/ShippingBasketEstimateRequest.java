package com.portcelana.natiart.dto.shipping;

import java.util.List;

public record ShippingBasketEstimateRequest(String zipCode, List<Item> items) {
    public record Item(String productId, int quantity, boolean personalized) {}
}
