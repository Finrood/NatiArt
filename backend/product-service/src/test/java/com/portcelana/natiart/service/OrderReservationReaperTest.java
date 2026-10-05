package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.portcelana.natiart.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderReservationReaperTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderManager orderManager;

    @Test
    void expiredPendingIdsAreCancelledThroughLifecycleService() {
        when(orderRepository.findPendingOrderIdsBefore(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of("order-1", "order-2"));
        when(orderRepository.scheduleReservationRetry(any(), any(), any())).thenReturn(1);
        final OrderReservationReaper reaper = new OrderReservationReaper(orderRepository, orderManager, 1000);

        reaper.expireAbandonedOrders();

        verify(orderManager).cancelPendingOrderInternally("order-1");
        verify(orderManager).cancelPendingOrderInternally("order-2");
    }

    @Test
    void failedCancellationDoesNotStopTheRemainingSweep() {
        when(orderRepository.findPendingOrderIdsBefore(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of("order-1", "order-2"));
        org.mockito.Mockito.doThrow(new RuntimeException("locked"))
                .when(orderManager)
                .cancelPendingOrderInternally("order-1");
        when(orderRepository.scheduleReservationRetry(any(), any(), any())).thenReturn(1);
        final OrderReservationReaper reaper = new OrderReservationReaper(orderRepository, orderManager, 1000);

        reaper.expireAbandonedOrders();

        verify(orderManager).cancelPendingOrderInternally("order-2");
    }

    @Test
    void invalidTtlIsRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> new OrderReservationReaper(orderRepository, orderManager, 0));
    }
}
