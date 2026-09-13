package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteResponse;
import com.portcelana.natiart.service.ShippingQuoteService;
import com.portcelana.natiart.service.ShippingService;

@RestController
public class ShippingController {

    private final ShippingService shippingService;
    private final ShippingQuoteService shippingQuoteService;

    public ShippingController(ShippingService shippingService, ShippingQuoteService shippingQuoteService) {
        this.shippingService = shippingService;
        this.shippingQuoteService = shippingQuoteService;
    }

    @PostMapping("/shipping/estimate")
    public List<ShippingEstimate> getShippingEstimates(@RequestBody ShippingEstimateRequest shippingEstimateRequest) {
        return shippingService.getShippingEstimates(shippingEstimateRequest);
    }

    @PostMapping("/shipping/quote")
    @PreAuthorize("isFullyAuthenticated()")
    public ShippingQuoteResponse createQuote(
            @RequestBody ShippingQuoteRequest request,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return shippingQuoteService.createQuote(request, principal == null ? null : principal.getExternalId());
    }
}
