package com.portcelana.natiart.dto.shipping;

import java.util.ArrayList;
import java.util.List;

public class ShippingQuoteRequest {
    private String zipCode;
    private List<ShippingQuoteItemRequest> items = new ArrayList<>();

    public String getZipCode() {
        return zipCode;
    }

    public ShippingQuoteRequest setZipCode(String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    public List<ShippingQuoteItemRequest> getItems() {
        return items;
    }

    public ShippingQuoteRequest setItems(List<ShippingQuoteItemRequest> items) {
        this.items = items;
        return this;
    }
}
