package com.portcelana.natiart.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;

/** Expires abandoned pending orders and delegates release to the locked order lifecycle. */
@Service
public class OrderReservationReaper {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderReservationReaper.class);

    private final OrderRepository orderRepository;
    private final OrderManager orderManager;
    private final long reservationTtlMillis;

    public OrderReservationReaper(
            OrderRepository orderRepository,
            OrderManager orderManager,
            @Value("${natiart.order.reservation.ttl-millis:1800000}") long reservationTtlMillis) {
        if (reservationTtlMillis <= 0) {
            throw new IllegalArgumentException("The order reservation TTL must be positive");
        }
        this.orderRepository = orderRepository;
        this.orderManager = orderManager;
        this.reservationTtlMillis = reservationTtlMillis;
    }

    @Scheduled(fixedDelayString = "${natiart.order.reservation.reaper-delay-millis:60000}")
    public void expireAbandonedOrders() {
        final Instant cutoff = Instant.now().minusMillis(reservationTtlMillis);
        orderRepository
                .findPendingOrderIdsBefore(OrderStatus.PENDING, cutoff, PageRequest.of(0, 100))
                .forEach(this::expireOne);
    }

    private void expireOne(String orderId) {
        try {
            orderManager.cancelPendingOrder(orderId, null);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not expire pending order [{}]: {}", orderId, e.getMessage());
        }
    }
}
