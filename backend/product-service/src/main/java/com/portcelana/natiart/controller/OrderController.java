package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderStatusUpdateDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.service.OrderManager;
import com.portcelana.natiart.service.OrderViewService;

@RestController
public class OrderController {
    private final OrderManager orderManager;
    private final OrderViewService orderViewService;

    public OrderController(OrderManager orderManager, OrderViewService orderViewService) {
        this.orderManager = orderManager;
        this.orderViewService = orderViewService;
    }

    @PostMapping("/orders/create")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto createOrder(
            @RequestBody OrderDto orderDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        final String ownerExternalId = principal != null ? principal.getExternalId() : null;
        final CustomerOrder created = orderManager.createOrder(orderDto, ownerExternalId, idempotencyKey);
        return orderViewService.getCustomerOrder(created.getId(), ownerExternalId);
    }

    @GetMapping("/orders")
    @PreAuthorize("isFullyAuthenticated()")
    public List<OrderDto> getCustomerOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return orderViewService.getCustomerOrders(principal.getExternalId(), page, size);
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto getCustomerOrder(
            @PathVariable String orderId, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return orderViewService.getCustomerOrder(orderId, principal.getExternalId());
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderDto> getFulfillmentOrders(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orderViewService.getFulfillmentOrders(page, size);
    }

    @PatchMapping("/admin/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderDto updateFulfillmentStatus(@PathVariable String orderId, @RequestBody OrderStatusUpdateDto update) {
        if (update == null || update.getStatus() == null) {
            throw new IllegalArgumentException("Order status is required");
        }
        return orderViewService.advanceFulfillmentStatus(orderId, update.getStatus());
    }

    @DeleteMapping("/orders/{orderId}")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto cancelOrder(
            @PathVariable String orderId, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        if (principal == null
                || principal.getExternalId() == null
                || principal.getExternalId().isBlank()) {
            throw new com.portcelana.natiart.controller.helper.UserNotAllowedException(
                    "An authenticated customer owner is required");
        }
        return orderManager.cancelPendingOrderResponse(orderId, principal.getExternalId());
    }
}
