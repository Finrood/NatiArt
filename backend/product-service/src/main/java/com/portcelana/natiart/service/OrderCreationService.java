package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.service.support.DomainValidation;
import com.portcelana.natiart.service.support.InputValidationException;

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
    private final ShippingService shippingService;

    public OrderCreationService(
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingService shippingService) {
        this.orderRepository = orderRepository;
        this.productManager = productManager;
        this.productRepository = productRepository;
        this.shippingService = shippingService;
    }

    @Transactional
    public CustomerOrder createOrder(
            OrderDto orderDto, String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        final String destinationCep = validateContactDetails(orderDto);
        validateItems(orderDto.getItems());
        final BigDecimal serverDeliveryAmount = shippingService.getOrderShippingAmount(destinationCep);
        requireRepresentableShippingAmount(serverDeliveryAmount);

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
                .setZipCode(destinationCep)
                .setStreet(orderDto.getStreet())
                .setComplement(orderDto.getComplement())
                .setDeliveryAmount(serverDeliveryAmount);

        BigDecimal totalItemsAmount = BigDecimal.ZERO;
        // Product reads are batched, while stock decrements remain atomic and
        // in this transaction so a failed line rolls back every reservation.
        final Map<String, Product> products = productManager.getProductsOrDie(orderDto.getItems().stream()
                .map(OrderItemDto::getProductId)
                .distinct()
                .toList());
        for (OrderItemDto item : orderDto.getItems()) {
            final Product product = products.get(item.getProductId());
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
            }
            final BigDecimal unitPrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            DomainValidation.money(unitPrice, "unitPrice", true);
            final int reserved = productRepository.decreaseStockIfAvailable(product.getId(), item.getQuantity());
            if (reserved == 0) {
                throw new IllegalArgumentException("Insufficient stock for product [" + product.getLabel() + "]");
            }
            totalItemsAmount = totalItemsAmount.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            customerOrder.addOrderItem(new CustomerOrderItem()
                    .setProduct(product)
                    .setQuantity(item.getQuantity())
                    .setPrice(unitPrice));
        }

        final BigDecimal totalAmount = totalItemsAmount.add(serverDeliveryAmount);
        DomainValidation.orderTotal(totalAmount);
        customerOrder.setTotalAmount(totalAmount);
        final CustomerOrder savedOrder = orderRepository.save(customerOrder);
        LOGGER.info(
                "Order created: orderId=[{}], owner=[{}], itemCount=[{}], totalAmount=[{}]",
                savedOrder.getId(),
                ownerExternalId,
                savedOrder.getItems().size(),
                savedOrder.getTotalAmount());
        return savedOrder;
    }

    static String validateContactDetails(OrderDto orderDto) {
        orderDto.setFirstname(DomainValidation.requiredText(orderDto.getFirstname(), "firstname", 255));
        orderDto.setLastname(DomainValidation.requiredText(orderDto.getLastname(), "lastname", 255));
        orderDto.setEmail(DomainValidation.requiredText(orderDto.getEmail(), "email", 255));
        orderDto.setPhone(DomainValidation.normalizedOptionalText(orderDto.getPhone(), "phone", 255));
        orderDto.setCountry(DomainValidation.requiredText(orderDto.getCountry(), "country", 255));
        orderDto.setState(DomainValidation.requiredText(orderDto.getState(), "state", 255));
        orderDto.setCity(DomainValidation.requiredText(orderDto.getCity(), "city", 255));
        orderDto.setNeighborhood(DomainValidation.requiredText(orderDto.getNeighborhood(), "neighborhood", 255));
        orderDto.setStreet(DomainValidation.requiredText(orderDto.getStreet(), "street", 255));
        orderDto.setComplement(DomainValidation.normalizedOptionalText(orderDto.getComplement(), "complement", 255));
        return DomainValidation.cep(orderDto.getZipCode());
    }

    private void validateItems(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new InputValidationException("items", "An order must contain at least one item");
        }
        if (items.size() > MAX_ORDER_LINES) {
            throw new InputValidationException(
                    "items", "An order must not contain more than " + MAX_ORDER_LINES + " items");
        }
        final Set<String> productIds = new HashSet<>();
        for (OrderItemDto item : items) {
            if (item == null) {
                throw new InputValidationException("items", "Every order item must be present");
            }
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                throw new InputValidationException("productId", "Every order item must reference a product");
            }
            if (!productIds.add(item.getProductId())) {
                throw new InputValidationException("items", "An order must not contain duplicate product lines");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InputValidationException("quantity", "Item quantities must be positive");
            }
            if (item.getQuantity() > MAX_ITEM_QUANTITY) {
                throw new InputValidationException("quantity", "Item quantities must not exceed " + MAX_ITEM_QUANTITY);
            }
        }
    }

    private void requireRepresentableShippingAmount(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.scale() > 2
                || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new UpstreamServiceException("Invalid shipping provider amount", HttpStatus.BAD_GATEWAY);
        }
    }
}
