package com.portcelana.natiart.dto.shipping;

import com.portcelana.natiart.dto.PersonalizationDto;

public class ShippingQuoteItemRequest {
    private String productId;
    private Integer quantity;
    private PersonalizationDto personalization;

    public String getProductId() {
        return productId;
    }

    public ShippingQuoteItemRequest setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public ShippingQuoteItemRequest setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    public PersonalizationDto getPersonalization() {
        return personalization;
    }

    public ShippingQuoteItemRequest setPersonalization(PersonalizationDto personalization) {
        this.personalization = personalization;
        return this;
    }
}
