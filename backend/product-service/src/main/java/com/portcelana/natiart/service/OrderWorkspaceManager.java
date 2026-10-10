package com.portcelana.natiart.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.*;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.*;

/** A bounded, oldest-first work queue that never changes customer or payment state. */
@Service
public class OrderWorkspaceManager {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderNotificationRepository notifications;

    public OrderWorkspaceManager(
            OrderRepository orders, PaymentRepository payments, OrderNotificationRepository notifications) {
        this.orders = orders;
        this.payments = payments;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public OrderWorkspaceDto overview() {
        return new OrderWorkspaceDto(
                orders.countByStatus(OrderStatus.PENDING),
                orders.countByStatus(OrderStatus.PAID),
                orders.countByStatus(OrderStatus.PROCESSING),
                orders.countByStatus(OrderStatus.SHIPPED),
                notifications.countAttention(java.time.Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> queue(OrderStatus status, int page, int size) {
        final List<String> ids = orders.findFulfillmentIdsByStatus(
                status, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), OrderManager.MAX_PAGE_SIZE)));
        if (ids.isEmpty()) return List.of();
        final Map<String, CustomerOrder> byId = orders.findAllWithItemsByIds(ids).stream()
                .collect(Collectors.toMap(CustomerOrder::getId, Function.identity()));
        return ids.stream()
                .filter(byId::containsKey)
                .map(id -> {
                    final CustomerOrder order = byId.get(id);
                    return OrderDto.from(order)
                            .setPaymentId(payments.findByOrderIdAndOwnerExternalId(id, order.getOwnerExternalId())
                                    .map(Payment::getId)
                                    .orElse(null));
                })
                .toList();
    }
}
