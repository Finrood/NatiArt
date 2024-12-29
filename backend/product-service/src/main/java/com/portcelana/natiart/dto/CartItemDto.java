package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.CartItem;

public class CartItemDto {
    private ProductDto productDto;
    private PersonalizationDto personalizationDto;
    private int quantity;

    public static CartItemDto from(CartItem cartItem) {
        return new CartItemDto()
                .setProductDto(ProductDto.from(cartItem.getProduct()))
                .setPersonalizationDto(PersonalizationDto.from(cartItem.getPersonalization()))
                .setQuantity(cartItem.getQuantity());
    }

    public ProductDto getProductDto() {
        return productDto;
    }

    public CartItemDto setProductDto(ProductDto productDto) {
        this.productDto = productDto;
        return this;
    }

    public PersonalizationDto getPersonalizationDto() {
        return personalizationDto;
    }

    public CartItemDto setPersonalizationDto(PersonalizationDto personalizationDto) {
        this.personalizationDto = personalizationDto;
        return this;
    }

    public int getQuantity() {
        return quantity;
    }

    public CartItemDto setQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }
}
