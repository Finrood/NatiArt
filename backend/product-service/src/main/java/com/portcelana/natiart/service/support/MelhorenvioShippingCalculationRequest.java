package com.portcelana.natiart.service.support;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;

public class MelhorenvioShippingCalculationRequest {
    private Address from;
    private Address to;
    private List<Volume> volumes;

    public static MelhorenvioShippingCalculationRequest from(
            ShippingEstimateRequest shippingEstimateRequest, String fromPostalCode) {
        return from(List.of(shippingEstimateRequest), fromPostalCode);
    }

    public static MelhorenvioShippingCalculationRequest from(
            List<ShippingEstimateRequest> shippingEstimateRequests, String fromPostalCode) {
        final MelhorenvioShippingCalculationRequest request = new MelhorenvioShippingCalculationRequest();

        final Address fromAddress = new Address();
        fromAddress.setPostal_code(fromPostalCode);
        request.setFrom(fromAddress);

        final Address toAddress = new Address();
        toAddress.setPostal_code(shippingEstimateRequests.get(0).getTo());
        request.setTo(toAddress);

        request.setVolumes(shippingEstimateRequests.stream()
                .filter(Objects::nonNull)
                .flatMap(item ->
                        java.util.stream.IntStream.range(0, item.getQuantity()).mapToObj(index -> toVolume(item)))
                .collect(Collectors.toList()));

        return request;
    }

    private static Volume toVolume(ShippingEstimateRequest shippingEstimateRequest) {
        final Volume volume = new Volume();
        volume.setHeight(shippingEstimateRequest.getHeight());
        volume.setWidth(shippingEstimateRequest.getWidth());
        volume.setLength(shippingEstimateRequest.getLength());
        volume.setWeight(shippingEstimateRequest.getWeight());
        return volume;
    }

    public Address getFrom() {
        return from;
    }

    public void setFrom(Address from) {
        this.from = from;
    }

    public Address getTo() {
        return to;
    }

    public void setTo(Address to) {
        this.to = to;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }
}

class Address {
    private String postal_code;

    public String getPostal_code() {
        return postal_code;
    }

    public void setPostal_code(String postal_code) {
        this.postal_code = postal_code;
    }
}

class Volume {
    private float height;
    private float width;
    private float length;
    private float weight;

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getLength() {
        return length;
    }

    public void setLength(float length) {
        this.length = length;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }
}
