package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.dto.ShipmentDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class ShipmentManagerTest {
    @Mock
    private OrderRepository orders;

    @InjectMocks
    private ShipmentManager manager;

    @Test
    void shipment_requiresPreparationAndValidCarrierReference() {
        assertThrows(IllegalArgumentException.class, () -> manager.ship("order", new ShipmentDto("", null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.ship("order", new ShipmentDto("BR123", "javascript:alert(1)")));
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.ship("order", new ShipmentDto("BR123", "https://user:password@carrier.test")));
        verifyNoInteractions(orders);
        final CustomerOrder pending = new CustomerOrder().setStatus(OrderStatus.PENDING);
        when(orders.findByIdForUpdate("order")).thenReturn(Optional.of(pending));
        assertThrows(IllegalArgumentException.class, () -> manager.ship("order", new ShipmentDto("BR123", null)));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void shipment_recordsMilestoneAndReplayingSameDetailsDoesNotChangeIt() {
        final CustomerOrder order = new CustomerOrder().setStatus(OrderStatus.PROCESSING);
        when(orders.findByIdForUpdate("order")).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        final ShipmentDto shipment = new ShipmentDto(" BR123 ", "https://carrier.test/BR123");
        final com.portcelana.natiart.dto.OrderDto response = manager.ship("order", shipment);
        assertEquals(OrderStatus.SHIPPED, response.getStatus());
        assertEquals("BR123", response.getTrackingCode());
        assertNotNull(response.getShippedAt());
        assertEquals(response.getShippedAt(), manager.ship("order", shipment).getShippedAt());
        verify(orders, times(1)).saveAndFlush(order);
        assertThrows(IllegalArgumentException.class, () -> manager.ship("order", new ShipmentDto("DIFFERENT", null)));
    }
}
