package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.service.ShippingService;

@RestController
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping("/shipping/estimate")
    public List<ShippingEstimate> getShippingEstimates(@RequestBody ShippingEstimateRequest shippingEstimateRequest) {
        return shippingService.getShippingEstimates(shippingEstimateRequest);
    }
}
