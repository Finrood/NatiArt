package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

/**
 * Owns the transaction that reserves stock and persists an order. Keeping
 * this transaction behind a separate Spring bean lets {@link OrderManagerImpl}
 * catch a unique-key race after the losing transaction has been rolled back
 * and reload the winner's order.
 */
@Service
public class OrderCreationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreationService.class);
    private static final int MAX_ITEM_QUANTITY = 100;
    private static final int MAX_ORDER_LINES = 50;

    private final OrderRepository orderRepository;
    private final ProductManager productManager;
    private final ProductRepository productRepository;
    private final ShippingQuoteService shippingQuoteService;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderCreationService(
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingQuoteService shippingQuoteService) {
        this.orderRepository = orderRepository;
        this.productManager = productManager;
        this.productRepository = productRepository;
        this.shippingQuoteService = shippingQuoteService;
    }

    @Transactional
    public CustomerOrder createOrder(
            OrderDto orderDto, String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        validateContactDetails(orderDto);
        validateItems(orderDto.getItems());
        final Map<String, Product> products;
        products = productManager.getProductsOrDie(orderDto.getItems().stream()
                .map(OrderItemDto::getProductId)
                .distinct()
                .toList());
        final ShippingQuote shippingQuote = shippingQuoteService.requireQuoteForOrder(
                orderDto.getShippingQuoteId(), ownerExternalId, orderDto.getZipCode(), orderDto.getItems(), products);
        final BigDecimal serverDeliveryAmount = shippingQuote.getShippingAmount();
        requireNonNegativeAmount(serverDeliveryAmount, "shipping amount");

        final CustomerOrder customerOrder = new CustomerOrder();
        customerOrder
                .setOrderDate(Instant.now())
                .setStatus(OrderStatus.PENDING)
                .setOwnerExternalId(ownerExternalId)
                .setIdempotencyKey(idempotencyKey)
                .setRequestFingerprint(requestFingerprint)
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
                .setDeliveryAmount(serverDeliveryAmount)
                .setShippingQuoteId(shippingQuote.getId())
                .setShippingServiceId(shippingQuote.getServiceId())
                .setShippingDestinationPostalCode(shippingQuote.getDestinationPostalCode())
                .setShippingQuoteExpiresAt(shippingQuote.getExpiresAt());

        BigDecimal totalItemsAmount = BigDecimal.ZERO;
        // Product reads are batched, while stock decrements remain atomic and
        // in this transaction so a failed line rolls back every reservation.
        for (OrderItemDto item : orderDto.getItems()) {
            final Product product = products.get(item.getProductId());
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
            }
            final int reserved = productRepository.decreaseStockIfAvailable(product.getId(), item.getQuantity());
            if (reserved == 0) {
                throw new IllegalArgumentException("Insufficient stock for product [" + product.getLabel() + "]");
            }
            final BigDecimal unitPrice =
                    shippingQuote.getItem(item.getProductId()).getUnitPrice();
            totalItemsAmount = totalItemsAmount.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            customerOrder.addOrderItem(new CustomerOrderItem()
                    .setProduct(product)
                    .setQuantity(item.getQuantity())
                    .setPrice(unitPrice));
        }

        customerOrder.setTotalAmount(totalItemsAmount.add(serverDeliveryAmount));
        final CustomerOrder savedOrder = orderRepository.save(customerOrder);
        LOGGER.info(
                "Order created: orderId=[{}], owner=[{}], itemCount=[{}], totalAmount=[{}]",
                savedOrder.getId(),
                ownerExternalId,
                savedOrder.getItems().size(),
                savedOrder.getTotalAmount());
        return savedOrder;
    }

    private void validateContactDetails(OrderDto orderDto) {
        requireNonBlankContact(orderDto.getFirstname(), "firstname");
        requireNonBlankContact(orderDto.getLastname(), "lastname");
        requireNonBlankContact(orderDto.getEmail(), "email");
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
            if (item == null) {
                throw new IllegalArgumentException("Every order item must be present");
            }
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
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "The " + field + " must be a non-negative value with at most two fraction digits");
        }
    }

    private void requireNonBlankContact(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Order " + field + " must not be blank");
        }
    }
}
