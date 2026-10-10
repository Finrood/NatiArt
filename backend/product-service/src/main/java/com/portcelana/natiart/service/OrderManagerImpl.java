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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceAlreadyExistsException;
import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.dto.PersonalizationDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.OrderReservationOwnerRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;

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
    private final com.portcelana.natiart.repository.ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final AsaasChargeSafetyService chargeSafetyService;

    @Autowired
    public OrderManagerImpl(
            OrderRepository orderRepository,
            OrderCreationService orderCreationService,
            com.portcelana.natiart.repository.ProductRepository productRepository,
            PaymentRepository paymentRepository,
            PaymentIdempotencyRepository paymentIdempotencyRepository,
            AsaasChargeSafetyService chargeSafetyService) {
        this.orderRepository = orderRepository;
        this.orderCreationService = orderCreationService;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.chargeSafetyService = chargeSafetyService;
    }

    /** Test-friendly constructor; production uses the transaction-owning bean above. */
    OrderManagerImpl(
            OrderReservationOwnerRepository reservationOwners,
            OrderRepository orderRepository,
            ProductManager productManager,
            com.portcelana.natiart.repository.ProductRepository productRepository,
            PaymentRepository paymentRepository,
            PaymentIdempotencyRepository paymentIdempotencyRepository,
            AsaasChargeSafetyService chargeSafetyService,
            ShippingQuoteService shippingQuoteService) {
        this(
                orderRepository,
                new OrderCreationService(
                        reservationOwners, orderRepository, productManager, productRepository, shippingQuoteService),
                productRepository,
                paymentRepository,
                paymentIdempotencyRepository,
                chargeSafetyService);
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
            current.setStatus(OrderStatus.PAID);
            return orderRepository.saveAndFlush(current);
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
    public List<CustomerOrder> getAllOrders(int page, int size) {
        final List<String> orderIds =
                orderRepository.findIds(pageRequest(page, size)).getContent();
        return loadOrders(orderIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrder> getOrdersForOwner(String ownerExternalId, int page, int size) {
        // An authenticated new account may still be awaiting its provider identity.
        // Never query with a missing owner: it cannot have any provider-owned orders.
        if (ownerExternalId == null || ownerExternalId.isBlank()) return List.of();
        final List<String> orderIds = orderRepository
                .findIdsByOwnerExternalId(ownerExternalId, pageRequest(page, size))
                .getContent();
        return loadOrders(orderIds);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrderForOwner(String orderId, String ownerExternalId) {
        requireOwner(ownerExternalId);
        return orderRepository
                .findByIdAndOwnerExternalIdWithItems(orderId, ownerExternalId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerOrder with id " + orderId + " not found"));
    }

    private void requireOwner(String ownerExternalId) {
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("An authenticated owner is required");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        final int safePage = Math.max(0, page);
        final int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "orderDate", "id"));
    }

    private List<CustomerOrder> loadOrders(List<String> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        final Map<String, CustomerOrder> byId = orderRepository.findAllWithItemsByIds(orderIds).stream()
                .collect(java.util.stream.Collectors.toMap(CustomerOrder::getId, order -> order));
        // The IN query does not guarantee order; restore the bounded page order
        // from the indexed id query before DTO mapping.
        return orderIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public CustomerOrder createOrder(OrderDto orderDto, String ownerExternalId, String idempotencyKey) {
        return createOrderForCustomer(orderDto, ownerExternalId, null, idempotencyKey);
    }

    @Override
    public CustomerOrder createGuestOrder(
            OrderDto orderDto, String ownerExternalId, String guestCustomerId, String idempotencyKey) {
        if (guestCustomerId == null || !guestCustomerId.matches("[a-f0-9-]{36}"))
            throw new IllegalArgumentException("Guest customer is required");
        return createOrderForCustomer(orderDto, ownerExternalId, guestCustomerId, idempotencyKey);
    }

    private CustomerOrder createOrderForCustomer(
            OrderDto orderDto, String ownerExternalId, String guestCustomerId, String idempotencyKey) {
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("An order must have an owner");
        }
        final String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        orderDto.setZipCode(OrderCreationService.validateContactDetails(orderDto));
        final String fingerprint = fingerprint(orderDto);

        if (normalizedKey != null) {
            final Optional<CustomerOrder> existing = findOrder(ownerExternalId, normalizedKey);
            if (existing.isPresent()) {
                return returnReplayOrReject(existing.get(), fingerprint);
            }
        }

        orderCreationService.prepareReservationOwner(ownerExternalId);
        try {
            return guestCustomerId == null
                    ? orderCreationService.createOrder(orderDto, ownerExternalId, normalizedKey, fingerprint)
                    : orderCreationService.createGuestOrder(
                            orderDto, ownerExternalId, normalizedKey, fingerprint, guestCustomerId);
        } catch (UnusableCustomerUploadException exception) {
            // A concurrent same-key creator may have claimed the artwork and committed first.
            // Reload its order before declaring the claim definitively rejected.
            if (normalizedKey != null) {
                final Optional<CustomerOrder> winner = findOrder(ownerExternalId, normalizedKey);
                if (winner.isPresent()) return returnReplayOrReject(winner.get(), fingerprint);
            }
            throw exception;
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            if (normalizedKey != null) {
                final Optional<CustomerOrder> winner = findOrder(ownerExternalId, normalizedKey);
                if (winner.isPresent()) return returnReplayOrReject(winner.get(), fingerprint);
            }
            throw new com.portcelana.natiart.controller.helper.OrderCreationRejectedException(e);
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
        append(canonical, order == null ? null : order.getHouseNumber());
        append(canonical, order == null ? null : order.getComplement());
        append(canonical, order == null ? null : order.getShippingQuoteId());

        final List<String> items = new ArrayList<>();
        if (order != null && order.getItems() != null) {
            for (OrderItemDto item : order.getItems()) {
                final StringBuilder itemValue = new StringBuilder();
                append(itemValue, item == null ? null : item.getProductId());
                append(itemValue, item == null ? null : item.getQuantity());
                append(itemValue, canonicalPersonalization(item == null ? null : item.getPersonalization()));
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

    private String canonicalPersonalization(PersonalizationDto personalization) {
        if (personalization == null || personalization.getPersonalizationOptions() == null) {
            return personalization == null ? "" : "invalid";
        }
        return personalization.getPersonalizationOptions().entrySet().stream()
                .sorted((left, right) -> String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey())))
                .map(entry -> String.valueOf(entry.getKey()) + "=" + String.valueOf(entry.getValue()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    @Override
    @Transactional
    public CustomerOrder updateOrderStatus(String orderId, OrderStatus status) {
        if (status == OrderStatus.CANCELLED) {
            return cancelPendingOrderInternally(orderId);
        }
        final CustomerOrder current = getOrderById(orderId);
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("Record carrier tracking through the shipment command");
        }
        if (current.getStatus() == null
                || !ALLOWED_TRANSITIONS
                        .getOrDefault(current.getStatus(), Set.of())
                        .contains(status)) {
            throw new IllegalArgumentException("Order [" + orderId + "] must not transition from ["
                    + current.getStatus() + "] to [" + status + "]");
        }
        current.setStatus(status);
        // Flush before returning so a concurrent transition fails at this boundary.
        // The entity version is checked and advanced by JPA.
        return orderRepository.saveAndFlush(current);
    }

    @Override
    @Transactional
    public CustomerOrder advanceFulfillmentStatus(String orderId, OrderStatus status) {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("Record carrier tracking through the shipment command");
        }
        if (status != OrderStatus.PROCESSING && status != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Administrators may only advance fulfillment status");
        }
        return updateOrderStatus(orderId, status);
    }

    @Override
    @Transactional
    public CustomerOrder cancelPendingOrder(String orderId, String requesterExternalId) {
        if (requesterExternalId == null || requesterExternalId.isBlank()) {
            throw new com.portcelana.natiart.controller.helper.UserNotAllowedException(
                    "An authenticated customer owner is required");
        }
        final CustomerOrder order = lockOrder(orderId);
        if (!requesterExternalId.equals(order.getOwnerExternalId())) {
            throw new com.portcelana.natiart.controller.helper.UserNotAllowedException(
                    "The authenticated user does not own this order");
        }
        return cancelLockedOrder(order);
    }

    @Override
    @Transactional
    public CustomerOrder cancelPendingOrderInternally(String orderId) {
        return cancelLockedOrder(lockOrder(orderId));
    }

    private CustomerOrder lockOrder(String orderId) {
        return orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerOrder with id " + orderId + " not found"));
    }

    private CustomerOrder cancelLockedOrder(CustomerOrder order) {
        final String orderId = order.getId();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only unpaid pending orders can be cancelled");
        }
        final List<PaymentIdempotency> attempts = paymentIdempotencyRepository.findByOrderId(orderId);
        if (!paymentIdempotencyRepository
                .findByOwnerExternalIdAndOrderIdIsNullAndStatusIn(
                        order.getOwnerExternalId(),
                        List.of(PaymentIdempotencyStatus.IN_PROGRESS, PaymentIdempotencyStatus.FAILED_RECOVERABLE))
                .isEmpty()) {
            throw new IllegalArgumentException("An unresolved payment attempt must be reconciled before cancellation");
        }
        final Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
        if (payment.isEmpty()) {
            if (!attempts.isEmpty()) {
                throw new IllegalArgumentException(
                        "A missing payment ledger row must be reconciled before cancellation");
            }
        } else {
            final Payment charge = payment.get();
            if (attempts.stream()
                    .anyMatch(attempt -> attempt.getStatus() == PaymentIdempotencyStatus.IN_PROGRESS
                            || !Objects.equals(attempt.getIdempotencyKey(), charge.getIdempotencyKey())
                            || (attempt.getProviderPaymentId() != null
                                    && !attempt.getProviderPaymentId().equals(charge.getId())))) {
                throw new IllegalArgumentException(
                        "An unresolved payment attempt must be reconciled before cancellation");
            }
            chargeSafetyService.ensureChargeInactive(charge);
            attempts.stream()
                    .filter(attempt -> attempt.getStatus() == PaymentIdempotencyStatus.FAILED_RECOVERABLE)
                    .forEach(attempt -> paymentIdempotencyRepository.save(attempt.setProviderPaymentId(charge.getId())
                            .setStatus(PaymentIdempotencyStatus.SUCCEEDED)));
        }
        order.getItems()
                .forEach(
                        item -> productRepository.restoreStock(item.getProduct().getId(), item.getQuantity()));
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public OrderDto cancelPendingOrderResponse(String orderId, String requesterExternalId) {
        return OrderDto.from(cancelPendingOrder(orderId, requesterExternalId));
    }
}
