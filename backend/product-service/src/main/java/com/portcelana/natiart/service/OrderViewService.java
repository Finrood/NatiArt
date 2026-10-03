package com.portcelana.natiart.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.PaymentRepository;

/** Builds complete order responses while their persistence context is open. */
@Service
public class OrderViewService {
    private final OrderManager orderManager;
    private final ObjectProvider<PaymentRepository> paymentRepositories;

    public OrderViewService(OrderManager orderManager, ObjectProvider<PaymentRepository> paymentRepositories) {
        this.orderManager = orderManager;
        this.paymentRepositories = paymentRepositories;
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getCustomerOrders(String ownerExternalId, int page, int size) {
        return orderManager.getOrdersForOwner(ownerExternalId, page, size).stream()
                .map(this::toOrderDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getCustomerOrder(String orderId, String ownerExternalId) {
        return toOrderDto(orderManager.getOrderForOwner(orderId, ownerExternalId));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getFulfillmentOrders(int page, int size) {
        return orderManager.getAllOrders(page, size).stream()
                .map(this::toOrderDto)
                .toList();
    }

    @Transactional
    public OrderDto advanceFulfillmentStatus(String orderId, OrderStatus status) {
        return toOrderDto(orderManager.advanceFulfillmentStatus(orderId, status));
    }

    private OrderDto toOrderDto(CustomerOrder order) {
        final OrderDto dto = OrderDto.from(order);
        final PaymentRepository paymentRepository = paymentRepositories.getIfAvailable();
        if (paymentRepository != null) {
            dto.setPaymentId(paymentRepository
                    .findByOrderIdAndOwnerExternalId(order.getId(), order.getOwnerExternalId())
                    .map(Payment::getId)
                    .orElse(null));
        }
        return dto;
    }
}
