package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.OrderNotificationDto;
import com.portcelana.natiart.service.OrderNotificationManager;

@RestController
public class OrderNotificationController {
    private final OrderNotificationManager jobs;

    public OrderNotificationController(OrderNotificationManager jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/admin/order-notifications/attention")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderNotificationDto> attention() {
        return jobs.attention();
    }

    @PostMapping("/admin/order-notifications/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public void retry(@PathVariable String id) {
        jobs.retry(id);
    }
}
