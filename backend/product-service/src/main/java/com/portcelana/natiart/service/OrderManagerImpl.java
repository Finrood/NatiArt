package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    // Bounds the whole request: every line costs a stock decrement plus an
    // insert inside one transaction, so an unbounded line list can time the
    // transaction out or blow up the database from a single POST.
    private static final int MAX_ORDER_LINES = 50;

    // Forward-only lifecycle: terminal states accept nothing, stages never
    // rewind or skip, so a stale retry cannot resurrect a delivered order or
    // rewind a paid one.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

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
    public CustomerOrder createOrder(OrderDto orderDto, String ownerExternalId) {
        validateItems(orderDto.getItems());
        requireNonNegativeAmount(orderDto.getDeliveryAmount(), "delivery amount");
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("An order must have an owner");
        }

        final CustomerOrder customerOrder = new CustomerOrder();
        customerOrder
                .setOrderDate(Instant.now())
                .setStatus(OrderStatus.PENDING)
                .setOwnerExternalId(ownerExternalId)
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
        // One batched product read for the whole order: the per-line stock
        // decrements below stay row-atomic on purpose, only the reads batch.
        final Map<String, Product> products = productManager.getProductsOrDie(orderDto.getItems().stream()
                .map(OrderItemDto::getProductId)
                .distinct()
                .toList());
        for (OrderItemDto item : orderDto.getItems()) {
            final Product product = products.get(item.getProductId());
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
        // Direct update by id: concurrent status writes serialize in the
        // database instead of colliding on @Version and surfacing
        // OptimisticLockException as a generic 500. The guard reads current
        // state first, so two racing transitions can still interleave with
        // last-write-wins -- accepted while no endpoint drives this path.
        final CustomerOrder current = getOrderById(orderId);
        if (current.getStatus() == null
                || !ALLOWED_TRANSITIONS
                        .getOrDefault(current.getStatus(), Set.of())
                        .contains(status)) {
            throw new IllegalArgumentException("Order [" + orderId + "] must not transition from ["
                    + current.getStatus() + "] to [" + status + "]");
        }
        if (orderRepository.updateStatusById(orderId, status) == 0) {
            throw new ResourceNotFoundException("CustomerOrder with id " + orderId + " not found");
        }
        return getOrderById(orderId);
    }

    private void validateItems(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        if (items.size() > MAX_ORDER_LINES) {
            throw new IllegalArgumentException("An order must not contain more than " + MAX_ORDER_LINES + " items");
        }
        final Set<String> productIds = new HashSet<>();
        for (OrderItemDto item : items) {
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                throw new IllegalArgumentException("Every order item must reference a product");
            }
            if (!productIds.add(item.getProductId())) {
                throw new IllegalArgumentException(
                        "An order must not contain duplicate product [" + item.getProductId() + "] lines");
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
