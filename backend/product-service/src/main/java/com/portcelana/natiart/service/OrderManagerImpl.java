package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderManagerImpl implements OrderManager {
    private final OrderRepository orderRepository;
    private final ProductManager productManager;
    private final ProductManagerImpl productManagerImpl;

    public OrderManagerImpl(OrderRepository orderRepository, ProductManager productManager, ProductManagerImpl productManagerImpl) {
        this.orderRepository = orderRepository;
        this.productManager = productManager;
        this.productManagerImpl = productManagerImpl;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerOrder with id " + orderId + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrder> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    @Transactional
    public CustomerOrder createOrder(OrderDto orderDto) {
        final BigDecimal totalItemsAmount = orderDto.getItems().stream()
                .peek(item -> {
                    final Product product = productManager.decreaseProductStockQuantityBy(item.getProductId(), item.getQuantity());
                    item.setPrice(product.getMarkedPrice());
                })
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final CustomerOrder customerOrder = new CustomerOrder();
        customerOrder.setOrderDate(Instant.now())
                .setStatus(OrderStatus.PENDING)
                .setFirstname(orderDto.getFirstname())
                .setLastname(orderDto.getLastname())
                .setEmail(orderDto.getEmail())
                .setPhone(orderDto.getPhone())
                .setCountry(orderDto.getCountry())
                .setState(orderDto.getState())
                .setCity(orderDto.getCity())
                .setNeighborhood(orderDto.getNeighborhood())
                .setZipCode(orderDto.getZipCode())
                .setStreet(orderDto.getStreet())
                .setComplement(orderDto.getComplement())
                .setDeliveryAmount(orderDto.getDeliveryAmount())
                .setTotalAmount(totalItemsAmount.add(customerOrder.getDeliveryAmount()));
        return orderRepository.save(customerOrder);
    }

    @Override
    @Transactional
    public CustomerOrder updateOrderStatus(String orderId, OrderStatus status) {
        final CustomerOrder customerOrder = getOrderById(orderId);
        customerOrder.setStatus(status);
        return orderRepository.save(customerOrder);
    }
}
