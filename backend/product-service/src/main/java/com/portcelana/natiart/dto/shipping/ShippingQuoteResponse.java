package com.portcelana.natiart.dto.shipping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.portcelana.natiart.model.ShippingQuote;

public class ShippingQuoteResponse {
    private String quoteId;
    private String destinationPostalCode;
    private String serviceId;
    private String serviceName;
    private Instant expiresAt;
    private BigDecimal itemAmount;
    private BigDecimal shippingAmount;
    private BigDecimal totalAmount;
    private List<ShippingQuoteItemResponse> items;

    public static ShippingQuoteResponse from(ShippingQuote quote) {
        return new ShippingQuoteResponse()
                .setQuoteId(quote.getId())
                .setDestinationPostalCode(quote.getDestinationPostalCode())
                .setServiceId(quote.getServiceId())
                .setServiceName(quote.getServiceName())
                .setExpiresAt(quote.getExpiresAt())
                .setItemAmount(quote.getItemAmount())
                .setShippingAmount(quote.getShippingAmount())
                .setTotalAmount(quote.getTotalAmount())
                .setItems(quote.getItems().stream()
                        .map(ShippingQuoteItemResponse::from)
                        .toList());
    }

    public String getQuoteId() {
        return quoteId;
    }

    public ShippingQuoteResponse setQuoteId(String quoteId) {
        this.quoteId = quoteId;
        return this;
    }

    public String getDestinationPostalCode() {
        return destinationPostalCode;
    }

    public ShippingQuoteResponse setDestinationPostalCode(String destinationPostalCode) {
        this.destinationPostalCode = destinationPostalCode;
        return this;
    }

    public String getServiceId() {
        return serviceId;
    }

    public ShippingQuoteResponse setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ShippingQuoteResponse setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public ShippingQuoteResponse setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public BigDecimal getItemAmount() {
        return itemAmount;
    }

    public ShippingQuoteResponse setItemAmount(BigDecimal itemAmount) {
        this.itemAmount = itemAmount;
        return this;
    }

    public BigDecimal getShippingAmount() {
        return shippingAmount;
    }

    public ShippingQuoteResponse setShippingAmount(BigDecimal shippingAmount) {
        this.shippingAmount = shippingAmount;
        return this;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public ShippingQuoteResponse setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public List<ShippingQuoteItemResponse> getItems() {
        return items;
    }

    public ShippingQuoteResponse setItems(List<ShippingQuoteItemResponse> items) {
        this.items = items;
        return this;
    }
}
