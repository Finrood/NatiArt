package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.*;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.service.OrderWorkspaceManager;

@RestController
public class OrderWorkspaceController {
    private final OrderWorkspaceManager workspace;

    public OrderWorkspaceController(OrderWorkspaceManager workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/admin/order-workspace")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderWorkspaceDto overview() {
        return workspace.overview();
    }

    @GetMapping("/admin/order-workspace/queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderDto> queue(
            @RequestParam OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return workspace.queue(status, page, size);
    }
}
