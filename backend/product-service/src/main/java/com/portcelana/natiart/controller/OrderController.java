package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.service.OrderManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderManager orderManager;

    public OrderController(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    @PostMapping("/orders/create")
    public OrderDto createOrder(@RequestBody OrderDto orderDto) {
        return OrderDto.from(orderManager.createOrder(orderDto));
    }
}
