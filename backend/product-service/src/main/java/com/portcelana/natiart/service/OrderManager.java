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
     * taken from the request body — the controller passes the principal's
     * external id (same identifier domain as
     * {@code Payment.ownerExternalId}, e.g. {@code cus_MINE}) so one user
     * cannot create orders on another user's behalf and a future
     * order-linked payment check can compare within one domain.
     */
    CustomerOrder createOrder(OrderDto order, String ownerExternalId, String idempotencyKey);

    default CustomerOrder createOrder(OrderDto order, String ownerExternalId) {
        return createOrder(order, ownerExternalId, null);
    }

    CustomerOrder updateOrderStatus(String orderId, OrderStatus status);

    /** Marks a payment-backed order as paid; repeated confirmations are safe. */
    CustomerOrder markOrderPaid(String orderId);
}
