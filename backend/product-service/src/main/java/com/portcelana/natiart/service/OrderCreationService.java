package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.dto.PersonalizationDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.model.support.PersonalizationOption;
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
    private static final int MAX_PERSONALIZATION_VALUE_LENGTH = 128;

    private final OrderRepository orderRepository;
    private final ProductManager productManager;
    private final ProductRepository productRepository;
    private final ShippingService shippingService;
    private final CustomerUploadService customerUploadService;
    private final BigDecimal personalizationSurcharge;

    @Autowired
    public OrderCreationService(
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingService shippingService,
            CustomerUploadService customerUploadService,
            @Value("${natiart.order.personalization-surcharge:0.00}") BigDecimal personalizationSurcharge) {
        this.orderRepository = orderRepository;
        this.productManager = productManager;
        this.productRepository = productRepository;
        this.shippingService = shippingService;
        this.customerUploadService = customerUploadService;
        requireNonNegativeAmount(personalizationSurcharge, "personalization surcharge");
        this.personalizationSurcharge = personalizationSurcharge;
    }

    /** Test-friendly constructor for order flows without personalization uploads. */
    OrderCreationService(
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingService shippingService) {
        this(orderRepository, productManager, productRepository, shippingService, null, BigDecimal.ZERO);
    }

    @Transactional
    public CustomerOrder createOrder(
            OrderDto orderDto, String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        validateContactDetails(orderDto);
        final Map<String, Integer> quantitiesByProduct = aggregateQuantities(orderDto.getItems());
        final BigDecimal serverDeliveryAmount = shippingService.getOrderShippingAmount(orderDto.getZipCode());
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
                .setDeliveryAmount(serverDeliveryAmount);

        BigDecimal totalItemsAmount = BigDecimal.ZERO;
        // Product reads are batched, while stock decrements remain atomic and
        // in this transaction so a failed line rolls back every reservation.
        final Map<String, Product> products = productManager.getProductsOrDie(orderDto.getItems().stream()
                .map(OrderItemDto::getProductId)
                .distinct()
                .toList());
        final List<ResolvedOrderItem> resolvedItems = new ArrayList<>();
        final Set<String> lineIdentities = new HashSet<>();
        for (OrderItemDto item : orderDto.getItems()) {
            final Product product = products.get(item.getProductId());
            if (product == null) {
                throw new IllegalArgumentException("Every order item must reference a known product");
            }
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
            }
            final PersonalizationSelection personalization =
                    resolvePersonalization(item.getPersonalization(), product, ownerExternalId);
            final String lineIdentity =
                    item.getProductId() + "\u0000" + canonicalPersonalization(personalization.options());
            if (!lineIdentities.add(lineIdentity)) {
                throw new IllegalArgumentException("An order must not contain duplicate fulfillment lines for product ["
                        + item.getProductId() + "]");
            }
            resolvedItems.add(new ResolvedOrderItem(item, product, personalization.personalization()));
        }

        // Stock is reserved by product, but resolvedItems below intentionally
        // remains one line per distinct fulfillment instruction.
        for (Map.Entry<String, Integer> entry : quantitiesByProduct.entrySet()) {
            final Product product = products.get(entry.getKey());
            final int reserved = productRepository.decreaseStockIfAvailable(product.getId(), entry.getValue());
            if (reserved == 0) {
                throw new IllegalArgumentException("Insufficient stock for product [" + product.getLabel() + "]");
            }
        }

        for (ResolvedOrderItem resolvedItem : resolvedItems) {
            final OrderItemDto item = resolvedItem.request();
            final Product product = resolvedItem.product();
            final BigDecimal unitPrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            final BigDecimal pricedUnit =
                    resolvedItem.personalization() == null ? unitPrice : unitPrice.add(personalizationSurcharge);
            totalItemsAmount = totalItemsAmount.add(pricedUnit.multiply(BigDecimal.valueOf(item.getQuantity())));
            customerOrder.addOrderItem(new CustomerOrderItem()
                    .setProduct(product)
                    .setQuantity(item.getQuantity())
                    .setPrice(pricedUnit)
                    .setPersonalization(resolvedItem.personalization()));
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

    private Map<String, Integer> aggregateQuantities(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        if (items.size() > MAX_ORDER_LINES) {
            throw new IllegalArgumentException("An order must not contain more than " + MAX_ORDER_LINES + " items");
        }
        final Map<String, Integer> quantitiesByProduct = new LinkedHashMap<>();
        final Set<String> lineIdentities = new HashSet<>();
        for (OrderItemDto item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Every order item must be present");
            }
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                throw new IllegalArgumentException("Every order item must reference a product");
            }
            if (!lineIdentities.add(
                    item.getProductId() + "\u0000" + canonicalRequestedPersonalization(item.getPersonalization()))) {
                throw new IllegalArgumentException("An order must not contain duplicate fulfillment lines for product ["
                        + item.getProductId() + "]");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantities must be positive");
            }
            if (item.getQuantity() > MAX_ITEM_QUANTITY) {
                throw new IllegalArgumentException("Item quantities must not exceed " + MAX_ITEM_QUANTITY);
            }
            final int aggregate = quantitiesByProduct.getOrDefault(item.getProductId(), 0) + item.getQuantity();
            if (aggregate > MAX_ITEM_QUANTITY) {
                throw new IllegalArgumentException("The combined quantity for product [" + item.getProductId()
                        + "] must not exceed " + MAX_ITEM_QUANTITY);
            }
            quantitiesByProduct.put(item.getProductId(), aggregate);
        }
        return quantitiesByProduct;
    }

    private String canonicalRequestedPersonalization(PersonalizationDto personalization) {
        if (personalization == null) {
            return "";
        }
        if (personalization.getPersonalizationOptions() == null) {
            return "invalid";
        }
        return canonicalPersonalization(personalization.getPersonalizationOptions());
    }

    private PersonalizationSelection resolvePersonalization(
            PersonalizationDto dto, Product product, String ownerExternalId) {
        if (dto == null) {
            return new PersonalizationSelection(Map.of(), null);
        }
        final Map<PersonalizationOption, String> requested = dto.getPersonalizationOptions();
        if (requested == null) {
            throw new IllegalArgumentException("Personalization options must be an object");
        }
        if (requested.isEmpty()) {
            return new PersonalizationSelection(Map.of(), null);
        }

        final Map<PersonalizationOption, String> accepted = new EnumMap<>(PersonalizationOption.class);
        CustomerUpload customImageUpload = null;
        for (Map.Entry<PersonalizationOption, String> option : requested.entrySet()) {
            final PersonalizationOption personalizationOption = option.getKey();
            final String value = option.getValue();
            if (personalizationOption == null
                    || value == null
                    || value.isBlank()
                    || value.length() > MAX_PERSONALIZATION_VALUE_LENGTH) {
                throw new IllegalArgumentException("Personalization options are invalid");
            }
            if (product.getAvailablePersonalizations() == null
                    || !product.getAvailablePersonalizations().contains(personalizationOption)) {
                throw new IllegalArgumentException("The requested personalization is not available for this product");
            }

            switch (personalizationOption) {
                case GOLDEN_BORDER -> {
                    if (!"true".equals(value)) {
                        throw new IllegalArgumentException("Golden border personalization must be true");
                    }
                    accepted.put(personalizationOption, value);
                }
                case CUSTOM_IMAGE -> {
                    try {
                        UUID.fromString(value);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Custom artwork upload is invalid");
                    }
                    if (customerUploadService == null) {
                        throw new IllegalArgumentException("Custom artwork uploads are unavailable");
                    }
                    customImageUpload = customerUploadService.claimForOrder(value, ownerExternalId);
                    accepted.put(personalizationOption, customImageUpload.getId());
                }
            }
        }

        final Personalization personalization = new Personalization().setPersonalizationOptions(accepted);
        if (customImageUpload != null) {
            personalization.setCustomImageUpload(customImageUpload);
        }
        return new PersonalizationSelection(accepted, personalization);
    }

    private String canonicalPersonalization(Map<PersonalizationOption, String> options) {
        return options.entrySet().stream()
                .sorted((left, right) -> String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey())))
                .map(entry -> String.valueOf(entry.getKey()) + "=" + String.valueOf(entry.getValue()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
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

    private record PersonalizationSelection(
            Map<PersonalizationOption, String> options, Personalization personalization) {}

    private record ResolvedOrderItem(OrderItemDto request, Product product, Personalization personalization) {}
}
