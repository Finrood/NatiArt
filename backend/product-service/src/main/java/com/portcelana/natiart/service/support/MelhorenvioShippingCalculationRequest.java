package com.portcelana.natiart.service.support;

import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;

import java.util.Collections;
import java.util.List;

import static com.portcelana.natiart.service.ShippingService.FROM_POSTAL_CODE;

public class MelhorenvioShippingCalculationRequest {
    private Address from;
    private Address to;
    private List<Volume> volumes;

    public static MelhorenvioShippingCalculationRequest from(ShippingEstimateRequest shippingEstimateRequest) {
        final MelhorenvioShippingCalculationRequest request = new MelhorenvioShippingCalculationRequest();

        final Address fromAddress = new Address();
        fromAddress.setPostal_code(FROM_POSTAL_CODE);
        request.setFrom(fromAddress);

        final Address toAddress = new Address();
        toAddress.setPostal_code(shippingEstimateRequest.getTo());
        request.setTo(toAddress);

        final Volume volume = new Volume();
        volume.setHeight(String.valueOf(shippingEstimateRequest.getHeight()));
        volume.setWidth(String.valueOf(shippingEstimateRequest.getWidth()));
        volume.setLength(String.valueOf(shippingEstimateRequest.getLength()));
        volume.setWeight(String.valueOf(shippingEstimateRequest.getWeight()));
        volume.setQntd(shippingEstimateRequest.getQuantity());

        request.setVolumes(Collections.singletonList(volume));

        return request;
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
    private String height;
    private String width;
    private String length;
    private String weight;
    private int qntd;

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getLength() {
        return length;
    }

    public void setLength(String length) {
        this.length = length;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public int getQntd() {
        return qntd;
    }

    public void setQntd(int qntd) {
        this.qntd = qntd;
    }
}
