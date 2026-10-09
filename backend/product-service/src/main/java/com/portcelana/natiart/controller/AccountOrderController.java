package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.*;
import com.portcelana.natiart.service.*;

@RestController
public class AccountOrderController {
    private final AccountOrderAccessManager access;
    private final OrderViewService views;
    private final OrderManager orders;

    public AccountOrderController(AccountOrderAccessManager access, OrderViewService views, OrderManager orders) {
        this.access = access;
        this.views = views;
        this.orders = orders;
    }

    @GetMapping("/account/orders")
    @PreAuthorize("isFullyAuthenticated()")
    public List<OrderDto> history(
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return access.history(principal, page, size);
    }

    @GetMapping("/account/orders/{id}")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto order(
            @PathVariable String id, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return views.getCustomerOrder(id, access.billingOwnerOrDie(principal, id));
    }

    @DeleteMapping("/account/orders/{id}")
    @PreAuthorize("isFullyAuthenticated()")
    public OrderDto cancel(
            @PathVariable String id, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return orders.cancelPendingOrderResponse(id, access.billingOwnerOrDie(principal, id));
    }
}
