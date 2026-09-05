package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

@Service
public class OrderManagerImpl implements OrderManager {
    // Anti-absurdity guard on a single order line; available stock remains the
    // real bound via the atomic decreaseStockIfAvailable check.
    private static final int MAX_ITEM_QUANTITY = 100;

    private final OrderRepository orderRepository;
    private final ProductManager productManager;
    private final ProductRepository productRepository;

    public OrderManagerImpl(
            OrderRepository orderRepository, ProductManager productManager, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productManager = productManager;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrderById(String orderId) {
        return orderRepository
                .findById(orderId)
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
        validateItems(orderDto.getItems());
        requireNonNegativeAmount(orderDto.getDeliveryAmount(), "delivery amount");

        final CustomerOrder customerOrder = new CustomerOrder();
        customerOrder
                .setOrderDate(Instant.now())
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
                .setDeliveryAmount(orderDto.getDeliveryAmount());

        BigDecimal totalItemsAmount = BigDecimal.ZERO;
        for (OrderItemDto item : orderDto.getItems()) {
            final Product product = productManager.getProductOrDie(item.getProductId());
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
            }
            final int reserved = productRepository.decreaseStockIfAvailable(product.getId(), item.getQuantity());
            if (reserved == 0) {
                throw new IllegalArgumentException("Insufficient stock for product [" + product.getLabel() + "]");
            }
            final BigDecimal unitPrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            totalItemsAmount = totalItemsAmount.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));

            final CustomerOrderItem orderItem = new CustomerOrderItem()
                    .setProduct(product)
                    .setQuantity(item.getQuantity())
                    .setPrice(unitPrice);
            customerOrder.addOrderItem(orderItem);
        }

        customerOrder.setTotalAmount(totalItemsAmount.add(customerOrder.getDeliveryAmount()));
        return orderRepository.save(customerOrder);
    }

    @Override
    @Transactional
    public CustomerOrder updateOrderStatus(String orderId, OrderStatus status) {
        final CustomerOrder customerOrder = getOrderById(orderId);
        customerOrder.setStatus(status);
        return orderRepository.save(customerOrder);
    }

    private void validateItems(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        for (OrderItemDto item : items) {
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                throw new IllegalArgumentException("Every order item must reference a product");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantities must be positive");
            }
            if (item.getQuantity() > MAX_ITEM_QUANTITY) {
                throw new IllegalArgumentException("Item quantities must not exceed " + MAX_ITEM_QUANTITY);
            }
        }
    }

    private void requireNonNegativeAmount(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("The " + field + " must be a non-negative value");
        }
    }
}
