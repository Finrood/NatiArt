package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.ShippingEstimate;
import com.portcelana.natiart.dto.ShippingEstimateRequest;
import com.portcelana.natiart.service.support.MelhorenvioShippingCalculationRequest;
import com.portcelana.natiart.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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