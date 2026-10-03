package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.support.OrderStatus;

public class OrderStatusUpdateDto {
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public OrderStatusUpdateDto setStatus(OrderStatus status) {
        this.status = status;
        return this;
    }
}
