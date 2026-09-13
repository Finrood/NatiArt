package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceAlreadyExistsException;
import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;

@Service
public class OrderManagerImpl implements OrderManager {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    private final OrderRepository orderRepository;
    private final OrderCreationService orderCreationService;

    @Autowired
    public OrderManagerImpl(OrderRepository orderRepository, OrderCreationService orderCreationService) {
        this.orderRepository = orderRepository;
        this.orderCreationService = orderCreationService;
    }

    /** Test-friendly constructor; production uses the transaction-owning bean above. */
    OrderManagerImpl(
            OrderRepository orderRepository,
            ProductManager productManager,
            com.portcelana.natiart.repository.ProductRepository productRepository,
            ShippingQuoteService shippingQuoteService) {
        this(
                orderRepository,
                new OrderCreationService(orderRepository, productManager, productRepository, shippingQuoteService));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrderById(String orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerOrder with id " + orderId + " not found"));
    }

    @Override
    @Transactional
    public CustomerOrder markOrderPaid(String orderId) {
        final CustomerOrder current = getOrderById(orderId);
        if (current.getStatus() == OrderStatus.PENDING) {
            return updateOrderStatus(orderId, OrderStatus.PAID);
        }
        if (current.getStatus() == OrderStatus.PAID
                || current.getStatus() == OrderStatus.PROCESSING
                || current.getStatus() == OrderStatus.SHIPPED
                || current.getStatus() == OrderStatus.DELIVERED) {
            return current;
        }
        throw new IllegalArgumentException(
                "A confirmed payment must not mark order [" + orderId + "] from status [" + current.getStatus() + "]");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrder> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public CustomerOrder createOrder(OrderDto orderDto, String ownerExternalId, String idempotencyKey) {
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("An order must have an owner");
        }
        final String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        final String fingerprint = fingerprint(orderDto);

        if (normalizedKey != null) {
            final Optional<CustomerOrder> existing = findOrder(ownerExternalId, normalizedKey);
            if (existing.isPresent()) {
                return returnReplayOrReject(existing.get(), fingerprint);
            }
        }

        try {
            return orderCreationService.createOrder(orderDto, ownerExternalId, normalizedKey, fingerprint);
        } catch (DataIntegrityViolationException e) {
            // The unique index is the serialization point. This code runs
            // after the losing transaction has rolled back, so reloading here
            // returns the winner and never decrements stock a second time.
            if (normalizedKey == null) {
                throw e;
            }
            final Optional<CustomerOrder> winner = findOrder(ownerExternalId, normalizedKey);
            if (winner.isEmpty()) {
                throw e;
            }
            return returnReplayOrReject(winner.get(), fingerprint);
        }
    }

    @Override
    public CustomerOrder createOrder(OrderDto orderDto, String ownerExternalId) {
        return createOrder(orderDto, ownerExternalId, null);
    }

    private Optional<CustomerOrder> findOrder(String ownerExternalId, String idempotencyKey) {
        return orderRepository.findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey);
    }

    private CustomerOrder returnReplayOrReject(CustomerOrder existingOrder, String fingerprint) {
        if (!Objects.equals(existingOrder.getRequestFingerprint(), fingerprint)) {
            throw new ResourceAlreadyExistsException("Idempotency-Key was already used for a different order");
        }
        return existingOrder;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        final String normalized = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("The Idempotency-Key header is invalid");
        }
        return normalized;
    }

    /** Hashes the complete client order shape so a key cannot be reused for another payload. */
    private String fingerprint(OrderDto order) {
        final StringBuilder canonical = new StringBuilder();
        append(canonical, order == null ? null : order.getFirstname());
        append(canonical, order == null ? null : order.getLastname());
        append(canonical, order == null ? null : order.getEmail());
        append(canonical, order == null ? null : order.getPhone());
        append(canonical, order == null ? null : order.getCountry());
        append(canonical, order == null ? null : order.getState());
        append(canonical, order == null ? null : order.getCity());
        append(canonical, order == null ? null : order.getNeighborhood());
        append(canonical, order == null ? null : order.getZipCode());
        append(canonical, order == null ? null : order.getStreet());
        append(canonical, order == null ? null : order.getComplement());
        append(canonical, order == null ? null : order.getShippingQuoteId());

        final List<String> items = new ArrayList<>();
        if (order != null && order.getItems() != null) {
            for (OrderItemDto item : order.getItems()) {
                final StringBuilder itemValue = new StringBuilder();
                append(itemValue, item == null ? null : item.getProductId());
                append(itemValue, item == null ? null : item.getQuantity());
                items.add(itemValue.toString());
            }
        }
        items.sort(Comparator.naturalOrder());
        append(canonical, String.join("", items));

        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private String normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros().toPlainString();
    }

    private void append(StringBuilder target, Object value) {
        if (value == null) {
            target.append("-1:");
            return;
        }
        final String text = String.valueOf(value);
        target.append(text.length()).append(':').append(text);
    }

    @Override
    @Transactional
    public CustomerOrder updateOrderStatus(String orderId, OrderStatus status) {
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
}
