package com.portcelana.natiart.service;

import java.util.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.*;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.repository.*;

/** Account access is independent of the immutable billing owner of a guest order. */
@Service
public class AccountOrderAccessManager {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderViewService views;

    public AccountOrderAccessManager(OrderRepository orders, PaymentRepository payments, OrderViewService views) {
        this.orders = orders;
        this.payments = payments;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public String billingOwnerOrDie(AuthenticationResponseDto.Principal account, String orderId) {
        if (account == null || account.getId() == null) throw new ResourceNotFoundException("Order not found");
        final CustomerOrder order =
                orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!(account.getExternalId() != null && account.getExternalId().equals(order.getOwnerExternalId()))
                && !account.getId().equals(order.getAccountOwnerId()))
            throw new ResourceNotFoundException("Order not found");
        return order.getOwnerExternalId();
    }

    @Transactional(readOnly = true)
    public String paymentOwnerOrDie(AuthenticationResponseDto.Principal account, String paymentId) {
        final com.portcelana.natiart.model.Payment payment =
                payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getOrderId() != null) return billingOwnerOrDie(account, payment.getOrderId());
        if (account == null
                || account.getExternalId() == null
                || !account.getExternalId().equals(payment.getOwnerExternalId()))
            throw new ResourceNotFoundException("Payment not found");
        return payment.getOwnerExternalId();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> history(AuthenticationResponseDto.Principal account, int page, int size) {
        if (account == null || account.getId() == null) return List.of();
        final List<String> ids = orders.findAccountIds(
                        account.getId(),
                        account.getExternalId(),
                        PageRequest.of(
                                Math.max(0, page),
                                Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Direction.DESC, "orderDate", "id")))
                .getContent();
        if (ids.isEmpty()) return List.of();
        final Map<String, CustomerOrder> found = orders.findAllWithItemsByIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(CustomerOrder::getId, value -> value));
        return ids.stream()
                .filter(found::containsKey)
                .map(id -> views.getCustomerOrder(id, found.get(id).getOwnerExternalId()))
                .toList();
    }
}
