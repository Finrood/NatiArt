package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;

import java.util.List;

public interface OrderManager {
    CustomerOrder getOrderById(String orderId);

    List<CustomerOrder> getAllOrders();

    CustomerOrder createOrder(OrderDto order);

    CustomerOrder updateOrderStatus(String orderId, OrderStatus status);
}
