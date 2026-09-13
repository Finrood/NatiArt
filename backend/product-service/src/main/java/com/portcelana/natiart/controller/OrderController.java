package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderStatusUpdateDto;
import com.portcelana.natiart.service.OrderManager;

@RestController
public class OrderController {
    private final OrderManager orderManager;

    public OrderController(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    @PostMapping("/orders/create")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto createOrder(
            @RequestBody OrderDto orderDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return OrderDto.from(orderManager.createOrder(
                orderDto, principal != null ? principal.getExternalId() : null, idempotencyKey));
    }

    @GetMapping("/orders")
    @PreAuthorize("isFullyAuthenticated()")
    public List<OrderDto> getCustomerOrders(@AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return orderManager.getOrdersForOwner(principal.getExternalId()).stream().map(OrderDto::from).toList();
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto getCustomerOrder(
            @PathVariable String orderId,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return OrderDto.from(orderManager.getOrderForOwner(orderId, principal.getExternalId()));
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderDto> getFulfillmentOrders() {
        return orderManager.getAllOrders().stream().map(OrderDto::from).toList();
    }

    @PatchMapping("/admin/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderDto updateFulfillmentStatus(
            @PathVariable String orderId, @RequestBody OrderStatusUpdateDto update) {
        if (update == null || update.getStatus() == null) {
            throw new IllegalArgumentException("Order status is required");
        }
        return OrderDto.from(orderManager.updateOrderStatus(orderId, update.getStatus()));
    }
}
