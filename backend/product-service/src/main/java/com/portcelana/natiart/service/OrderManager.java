package com.portcelana.natiart.service;

import java.util.List;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;

public interface OrderManager {
    CustomerOrder getOrderById(String orderId);

    List<CustomerOrder> getAllOrders();

    /**
     * Persists a new order owned by the authenticated user. The owner is never
     * taken from the request body — the controller passes the resolved
     * principal so one user cannot create orders on another user's behalf.
     */
    CustomerOrder createOrder(OrderDto order, String ownerExternalId);

    CustomerOrder updateOrderStatus(String orderId, OrderStatus status);
}
