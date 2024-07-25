package com.portcelana.natiart.service.support;

import java.math.BigDecimal;
import java.util.List;

public class MelhorenvioShippingCalculationResponse {
    private int id;
    private String name;
    private BigDecimal price;
    private BigDecimal custom_price;
    private BigDecimal discount;
    private String currency;
    private int delivery_time;
    private DeliveryRange delivery_range;
    private int custom_delivery_time;
    private DeliveryRange custom_delivery_range;
    private List<Package> packages;
    private AdditionalServices additional_services;
    private Company company;
    private String error;

    public MelhorenvioShippingCalculationResponse() {
    }

    // Getters and setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getCustom_price() {
        return custom_price;
    }

    public void setCustom_price(BigDecimal custom_price) {
        this.custom_price = custom_price;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getDelivery_time() {
        return delivery_time;
    }

    public void setDelivery_time(int delivery_time) {
        this.delivery_time = delivery_time;
    }

    public DeliveryRange getDelivery_range() {
        return delivery_range;
    }

    public void setDelivery_range(DeliveryRange delivery_range) {
        this.delivery_range = delivery_range;
    }

    public int getCustom_delivery_time() {
        return custom_delivery_time;
    }

    public void setCustom_delivery_time(int custom_delivery_time) {
        this.custom_delivery_time = custom_delivery_time;
    }

    public DeliveryRange getCustom_delivery_range() {
        return custom_delivery_range;
    }

    public void setCustom_delivery_range(DeliveryRange custom_delivery_range) {
        this.custom_delivery_range = custom_delivery_range;
    }

    public List<Package> getPackages() {
        return packages;
    }

    public void setPackages(List<Package> packages) {
        this.packages = packages;
    }

    public AdditionalServices getAdditional_services() {
        return additional_services;
    }

    public void setAdditional_services(AdditionalServices additional_services) {
        this.additional_services = additional_services;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getCompanyName() {
        if (company == null) {
            return null;
        }
        return company.getName();
    }
}

class DeliveryRange {
    private int min;
    private int max;

    public DeliveryRange() {
    }

    // Getters and setters

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }
}

class Package {
    private String price;
    private String discount;
    private String format;
    private Dimensions dimensions;
    private String weight;
    private String insurance_value;

    public Package() {
    }

    // Getters and setters

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getDiscount() {
        return discount;
    }

    public void setDiscount(String discount) {
        this.discount = discount;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public void setDimensions(Dimensions dimensions) {
        this.dimensions = dimensions;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public String getInsurance_value() {
        return insurance_value;
    }

    public void setInsurance_value(String insurance_value) {
        this.insurance_value = insurance_value;
    }
}

class Dimensions {
    private int height;
    private int width;
    private int length;

    public Dimensions() {
    }

    // Getters and setters

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
}

class AdditionalServices {
    private boolean receipt;
    private boolean own_hand;
    private boolean collect;

    public AdditionalServices() {
    }

    // Getters and setters

    public boolean isReceipt() {
        return receipt;
    }

    public void setReceipt(boolean receipt) {
        this.receipt = receipt;
    }

    public boolean isOwn_hand() {
        return own_hand;
    }

    public void setOwn_hand(boolean own_hand) {
        this.own_hand = own_hand;
    }

    public boolean isCollect() {
        return collect;
    }

    public void setCollect(boolean collect) {
        this.collect = collect;
    }
}

class Company {
    private int id;
    private String name;
    private String picture;

    public Company() {
    }

    // Getters and setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }
}
