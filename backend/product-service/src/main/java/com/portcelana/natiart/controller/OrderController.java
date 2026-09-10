package com.portcelana.natiart.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.service.OrderManager;

@RestController
public class OrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    private final OrderManager orderManager;

    public OrderController(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    @PostMapping("/orders/create")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto createOrder(
            @RequestBody OrderDto orderDto, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        final String ownerExternalId = principal != null ? principal.getExternalId() : null;
        LOGGER.info("Creating order for customer [{}]", ownerExternalId);
        return OrderDto.from(orderManager.createOrder(orderDto, ownerExternalId));
    }
}
