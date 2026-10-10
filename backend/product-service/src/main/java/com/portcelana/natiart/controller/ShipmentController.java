package com.portcelana.natiart.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.ShipmentDto;
import com.portcelana.natiart.service.ShipmentManager;

@RestController
public class ShipmentController {
    private final ShipmentManager shipments;

    public ShipmentController(ShipmentManager shipments) {
        this.shipments = shipments;
    }

    @PostMapping("/admin/orders/{id}/shipment")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderDto ship(@PathVariable String id, @RequestBody ShipmentDto shipment) {
        return shipments.ship(id, shipment);
    }
}
