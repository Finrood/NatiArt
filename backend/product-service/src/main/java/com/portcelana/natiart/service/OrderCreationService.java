package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ShippingQuoteNotValidException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.dto.PersonalizationDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.ShippingQuoteItem;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.OrderReservationOwnerRepository;
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
    private final OrderReservationOwnerRepository reservationOwners;
    private final ProductManager productManager;
    private final ProductRepository productRepository;
    private final ShippingQuoteService shippingQuoteService;
    private final CustomerUploadService customerUploadService;
    private final BigDecimal personalizationSurcharge;
    private final int maxOutstandingReservations;

    @Autowired
    public OrderCreationService(
            OrderReservationOwnerRepository reservationOwners,
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingQuoteService shippingQuoteService,
            CustomerUploadService customerUploadService,
            @Value("${natiart.order.personalization-surcharge:0.00}") BigDecimal personalizationSurcharge,
            @Value("${natiart.order.max-outstanding-reservations:5}") int maxOutstandingReservations) {
        this.reservationOwners = reservationOwners;
        this.orderRepository = orderRepository;
        this.productManager = productManager;
        this.productRepository = productRepository;
        this.shippingQuoteService = shippingQuoteService;
        this.customerUploadService = customerUploadService;
        requireRepresentableShippingAmount(personalizationSurcharge);
        this.personalizationSurcharge = personalizationSurcharge;
        if (maxOutstandingReservations <= 0)
            throw new IllegalArgumentException("The outstanding reservation limit must be positive");
        this.maxOutstandingReservations = maxOutstandingReservations;
    }

    /** Test-friendly constructor for order flows without personalization uploads. */
    OrderCreationService(
            OrderReservationOwnerRepository reservationOwners,
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingQuoteService shippingQuoteService) {
        this(
                reservationOwners,
                orderRepository,
                productManager,
                productRepository,
                shippingQuoteService,
                null,
                BigDecimal.ZERO,
                5);
    }

    public OrderCreationService(
            OrderReservationOwnerRepository reservationOwners,
            OrderRepository orderRepository,
            ProductManager productManager,
            ProductRepository productRepository,
            ShippingQuoteService shippingQuoteService,
            CustomerUploadService customerUploadService,
            BigDecimal personalizationSurcharge) {
        this(
                reservationOwners,
                orderRepository,
                productManager,
                productRepository,
                shippingQuoteService,
                customerUploadService,
                personalizationSurcharge,
                5);
    }

    /** Prepares the account lock row before starting the stock/order transaction. */
    public void prepareReservationOwner(String ownerExternalId) {
        try {
            reservationOwners.initializeOwner(ownerExternalId);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // The independent losing insert rolled back; only a committed winner permits proceeding.
            if (!reservationOwners.existsById(ownerExternalId)) {
                throw concurrentInsert;
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerOrder createOrder(
            OrderDto orderDto, String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        return createForCustomer(orderDto, ownerExternalId, idempotencyKey, requestFingerprint, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerOrder createGuestOrder(
            OrderDto orderDto,
            String ownerExternalId,
            String idempotencyKey,
            String requestFingerprint,
            String guestCustomerId) {
        return createForCustomer(orderDto, ownerExternalId, idempotencyKey, requestFingerprint, guestCustomerId);
    }

    private CustomerOrder createForCustomer(
            OrderDto orderDto,
            String ownerExternalId,
            String idempotencyKey,
            String requestFingerprint,
            String guestCustomerId) {
        reservationOwners
                .findByOwnerExternalIdForUpdate(ownerExternalId)
                .orElseThrow(() -> new IllegalStateException("Reservation owner has not been initialized"));
        if (orderRepository.countByOwnerExternalIdAndStatus(ownerExternalId, OrderStatus.PENDING)
                >= maxOutstandingReservations) {
            throw new IllegalArgumentException("Too many unpaid orders are reserved for this account");
        }
        final String destinationCep = validateContactDetails(orderDto);
        final Map<String, Integer> quantitiesByProduct = aggregateQuantities(orderDto.getItems());
        final Map<String, Product> products = productManager.getProductsOrDie(orderDto.getItems().stream()
                .map(OrderItemDto::getProductId)
                .distinct()
                .toList());
        final ShippingQuote shippingQuote = shippingQuoteService.requireQuoteForOrder(
                orderDto.getShippingQuoteId(), ownerExternalId, orderDto.getZipCode(), orderDto.getItems(), products);
        final BigDecimal serverDeliveryAmount = shippingQuote.getShippingAmount();
        requireRepresentableShippingAmount(serverDeliveryAmount);

        final CustomerOrder customerOrder = new CustomerOrder();
        customerOrder
                .setOrderDate(Instant.now())
                .setStatus(OrderStatus.PENDING)
                .setOwnerExternalId(ownerExternalId)
                .setGuestCustomerId(guestCustomerId)
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
                .setHouseNumber(orderDto.getHouseNumber())
                .setComplement(orderDto.getComplement())
                .setDeliveryAmount(serverDeliveryAmount)
                .setShippingQuoteId(shippingQuote.getId())
                .setShippingServiceId(shippingQuote.getServiceId())
                .setShippingDestinationPostalCode(shippingQuote.getDestinationPostalCode())
                .setShippingQuoteExpiresAt(shippingQuote.getExpiresAt());

        BigDecimal totalItemsAmount = BigDecimal.ZERO;
        // Product reads are batched, while stock decrements remain atomic and
        // in this transaction so a failed line rolls back every reservation.
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
            DomainValidation.money(product.getMarkedPrice().orElseGet(product::getOriginalPrice), "unitPrice", true);
            final PersonalizationSelection personalization =
                    resolvePersonalization(item.getPersonalization(), product, ownerExternalId);
            final String personalizationKey = PersonalizationRules.canonical(personalization.options());
            final String lineIdentity = item.getProductId() + "\u0000" + personalizationKey;
            if (!lineIdentities.add(lineIdentity)) {
                throw new IllegalArgumentException("An order must not contain duplicate fulfillment lines for product ["
                        + item.getProductId() + "]");
            }
            resolvedItems.add(
                    new ResolvedOrderItem(item, product, personalization.personalization(), personalizationKey));
        }

        DomainValidation.orderTotal(shippingQuote.getTotalAmount());
        // Stock is reserved by product, but resolvedItems below intentionally
        // remains one line per distinct fulfillment instruction.
        // Acquire each product row in a stable order while preserving fulfillment line order.
        for (String productId : quantitiesByProduct.keySet().stream().sorted().toList()) {
            final Product product = products.get(productId);
            final int reserved =
                    productRepository.decreaseStockIfAvailable(product.getId(), quantitiesByProduct.get(productId));
            if (reserved == 0) {
                throw new IllegalArgumentException("Insufficient stock for product [" + product.getLabel() + "]");
            }
        }

        for (ResolvedOrderItem resolvedItem : resolvedItems) {
            final OrderItemDto item = resolvedItem.request();
            final Product product = resolvedItem.product();
            final BigDecimal unitPrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            final ShippingQuoteItem quoted =
                    shippingQuote.getItem(item.getProductId(), resolvedItem.personalizationKey());
            final BigDecimal expectedUnit =
                    resolvedItem.personalization() == null ? unitPrice : unitPrice.add(personalizationSurcharge);
            if (quoted == null || quoted.getUnitPrice().compareTo(expectedUnit) != 0) {
                throw new ShippingQuoteNotValidException("The shipping quote no longer matches the order");
            }
            final BigDecimal pricedUnit = quoted.getUnitPrice();
            DomainValidation.money(pricedUnit, "unitPrice", true);
            totalItemsAmount = totalItemsAmount.add(pricedUnit.multiply(BigDecimal.valueOf(item.getQuantity())));
            customerOrder.addOrderItem(new CustomerOrderItem()
                    .setProduct(product)
                    .setProductLabel(product.getLabel())
                    .setProductSku(product.getId())
                    .setQuantity(item.getQuantity())
                    .setPrice(pricedUnit)
                    .setPersonalization(resolvedItem.personalization()));
        }

        if (totalItemsAmount.compareTo(shippingQuote.getItemAmount()) != 0
                || totalItemsAmount.add(serverDeliveryAmount).compareTo(shippingQuote.getTotalAmount()) != 0) {
            throw new ShippingQuoteNotValidException("The shipping quote no longer matches the order total");
        }
        customerOrder.setTotalAmount(shippingQuote.getTotalAmount());
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
        orderDto.setHouseNumber(DomainValidation.requiredText(orderDto.getHouseNumber(), "houseNumber", 255));
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

    private Map<String, Integer> aggregateQuantities(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new InputValidationException("items", "An order must contain at least one item");
        }
        if (items.size() > MAX_ORDER_LINES) {
            throw new InputValidationException(
                    "items", "An order must not contain more than " + MAX_ORDER_LINES + " items");
        }
        final Map<String, Integer> quantitiesByProduct = new LinkedHashMap<>();
        final Set<String> lineIdentities = new HashSet<>();
        for (OrderItemDto item : items) {
            if (item == null) {
                throw new InputValidationException("items", "Every order item must be present");
            }
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                throw new InputValidationException("productId", "Every order item must reference a product");
            }
            if (!lineIdentities.add(
                    item.getProductId() + "\u0000" + canonicalRequestedPersonalization(item.getPersonalization()))) {
                throw new IllegalArgumentException("An order must not contain duplicate fulfillment lines for product ["
                        + item.getProductId() + "]");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InputValidationException("quantity", "Item quantities must be positive");
            }
            if (item.getQuantity() > MAX_ITEM_QUANTITY) {
                throw new InputValidationException("quantity", "Item quantities must not exceed " + MAX_ITEM_QUANTITY);
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
        return PersonalizationRules.canonical(personalization.getPersonalizationOptions());
    }

    private PersonalizationSelection resolvePersonalization(
            PersonalizationDto dto, Product product, String ownerExternalId) {
        if (dto == null) {
            return new PersonalizationSelection(Map.of(), null);
        }
        final Map<PersonalizationOption, String> accepted = PersonalizationRules.validatedOptions(dto, product);
        if (accepted.isEmpty()) {
            return new PersonalizationSelection(Map.of(), null);
        }
        CustomerUpload customImageUpload = null;
        if (accepted.containsKey(PersonalizationOption.CUSTOM_IMAGE)) {
            if (customerUploadService == null) {
                throw new IllegalArgumentException("Custom artwork uploads are unavailable");
            }
            customImageUpload = customerUploadService.claimForOrder(
                    accepted.get(PersonalizationOption.CUSTOM_IMAGE), ownerExternalId);
        }

        final Personalization personalization = new Personalization().setPersonalizationOptions(accepted);
        if (customImageUpload != null) {
            personalization.setCustomImageUpload(customImageUpload);
        }
        return new PersonalizationSelection(accepted, personalization);
    }

    private void requireRepresentableShippingAmount(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.scale() > 2
                || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new UpstreamServiceException("Invalid shipping provider amount", HttpStatus.BAD_GATEWAY);
        }
    }

    private record PersonalizationSelection(
            Map<PersonalizationOption, String> options, Personalization personalization) {}

    private record ResolvedOrderItem(
            OrderItemDto request, Product product, Personalization personalization, String personalizationKey) {}
}
