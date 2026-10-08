package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ShippingQuoteNotValidException;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.dto.shipping.ShippingBasketEstimateRequest;
import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteResponse;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.ShippingQuoteItem;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.repository.ShippingQuoteRepository;

@Service
public class ShippingQuoteService {
    private static final int MAX_ITEM_QUANTITY = 100;
    private static final int MAX_ORDER_LINES = 50;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ProductRepository productRepository;
    private final ShippingQuoteRepository shippingQuoteRepository;
    private final ShippingService shippingService;
    private final CustomerUploadService customerUploadService;
    private final Clock clock;
    private final Duration quoteTtl;
    private final BigDecimal personalizationSurcharge;

    @Autowired
    public ShippingQuoteService(
            ProductRepository productRepository,
            ShippingQuoteRepository shippingQuoteRepository,
            ShippingService shippingService,
            CustomerUploadService customerUploadService,
            @Value("${natiart.shipping.quote-ttl-seconds:900}") long quoteTtlSeconds,
            @Value("${natiart.order.personalization-surcharge:0.00}") BigDecimal personalizationSurcharge) {
        this(
                productRepository,
                shippingQuoteRepository,
                shippingService,
                customerUploadService,
                Clock.systemUTC(),
                quoteTtlSeconds,
                personalizationSurcharge);
    }

    ShippingQuoteService(
            ProductRepository productRepository,
            ShippingQuoteRepository shippingQuoteRepository,
            ShippingService shippingService,
            Clock clock,
            long quoteTtlSeconds) {
        this(productRepository, shippingQuoteRepository, shippingService, null, clock, quoteTtlSeconds, ZERO);
    }

    ShippingQuoteService(
            ProductRepository productRepository,
            ShippingQuoteRepository shippingQuoteRepository,
            ShippingService shippingService,
            CustomerUploadService customerUploadService,
            Clock clock,
            long quoteTtlSeconds,
            BigDecimal personalizationSurcharge) {
        if (quoteTtlSeconds < 1) {
            throw new IllegalArgumentException("Shipping quote TTL must be positive");
        }
        this.productRepository = productRepository;
        this.shippingQuoteRepository = shippingQuoteRepository;
        this.shippingService = shippingService;
        this.customerUploadService = customerUploadService;
        this.clock = clock;
        this.quoteTtl = Duration.ofSeconds(quoteTtlSeconds);
        requireMoney(personalizationSurcharge, "personalization surcharge");
        this.personalizationSurcharge = personalizationSurcharge;
    }

    @Transactional
    public ShippingQuoteResponse createQuote(ShippingQuoteRequest request, String ownerExternalId) {
        requireOwner(ownerExternalId);
        final String destination = normalizePostalCode(request == null ? null : request.getZipCode());
        final List<ShippingQuoteItemRequest> requestedItems =
                validateItems(request == null ? null : request.getItems());
        final List<String> productIds = requestedItems.stream()
                .map(ShippingQuoteItemRequest::getProductId)
                .distinct()
                .toList();
        final Map<String, Product> products = loadProducts(productIds);

        final List<ShippingQuoteItem> quoteItems = new ArrayList<>();
        final List<ShippingEstimateRequest> volumes = new ArrayList<>();
        final Map<String, Integer> quantitiesByProduct = new LinkedHashMap<>();
        final Set<String> lineIdentities = new HashSet<>();
        final Set<String> artworkIds = new HashSet<>();
        BigDecimal itemAmount = ZERO;
        for (ShippingQuoteItemRequest requestedItem : requestedItems) {
            final Product product = products.get(requestedItem.getProductId());
            requireProductShippingData(product);
            final Map<PersonalizationOption, String> options =
                    PersonalizationRules.validatedOptions(requestedItem.getPersonalization(), product);
            final String personalizationKey = PersonalizationRules.canonical(options);
            if (!lineIdentities.add(product.getId() + "\u0000" + personalizationKey)) {
                throw new IllegalArgumentException("A shipping quote must not contain duplicate fulfillment lines");
            }
            if (options.containsKey(PersonalizationOption.CUSTOM_IMAGE)) {
                final String uploadId = options.get(PersonalizationOption.CUSTOM_IMAGE);
                if (!artworkIds.add(uploadId)) {
                    throw new IllegalArgumentException("An artwork upload can only be used once per order");
                }
                if (customerUploadService == null) {
                    throw new IllegalArgumentException("Custom artwork uploads are unavailable");
                }
                customerUploadService.requireClaimableForQuote(uploadId, ownerExternalId);
            }
            final BigDecimal basePrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            final BigDecimal unitPrice = options.isEmpty() ? basePrice : basePrice.add(personalizationSurcharge);
            requireMoney(unitPrice, "product price");
            final int quantity = requestedItem.getQuantity();
            quoteItems.add(new ShippingQuoteItem(
                    product.getId(),
                    personalizationKey,
                    quantity,
                    money(unitPrice, "product price"),
                    product.getVersion()));
            itemAmount = itemAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            final int aggregate = quantitiesByProduct.getOrDefault(product.getId(), 0) + quantity;
            if (aggregate > MAX_ITEM_QUANTITY) {
                throw new IllegalArgumentException("The combined quantity for a product exceeds " + MAX_ITEM_QUANTITY);
            }
            quantitiesByProduct.put(product.getId(), aggregate);
            final Package packaging = product.getPackaging()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product [" + product.getLabel() + "] has no shipping package configured"));
            volumes.add(new ShippingEstimateRequest(
                    destination,
                    product.getWeightKg().floatValue(),
                    packaging.getDepth(),
                    packaging.getWidth(),
                    packaging.getHeight(),
                    quantity,
                    money(unitPrice, "insurance value")));
        }

        com.portcelana.natiart.service.support.DomainValidation.orderTotal(itemAmount);
        final ShippingEstimate estimate = shippingService.getShippingEstimates(volumes).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No shipping options are available for this address"));
        final String serviceId = estimate.getServiceId();
        if (serviceId == null
                || serviceId.isBlank()
                || estimate.getService() == null
                || estimate.getService().isBlank()) {
            throw new IllegalArgumentException("The shipping provider returned an unusable service");
        }
        final BigDecimal shippingAmount = money(estimate.getPrice(), "shipping amount");
        final BigDecimal normalizedItemAmount = money(itemAmount, "item amount");
        final BigDecimal totalAmount = normalizedItemAmount.add(shippingAmount).setScale(2);
        com.portcelana.natiart.service.support.DomainValidation.orderTotal(totalAmount);
        final ShippingQuote quote = new ShippingQuote()
                .setOwnerExternalId(ownerExternalId)
                .setDestinationPostalCode(destination)
                .setServiceId(serviceId)
                .setServiceName(estimate.getService())
                .setEstimatedDeliveryDays(estimate.getEstimatedDeliveryDays())
                .setItemAmount(normalizedItemAmount)
                .setShippingAmount(shippingAmount)
                .setTotalAmount(totalAmount)
                .setExpiresAt(clock.instant().plus(quoteTtl))
                .setRequestFingerprint(fingerprint(destination, quoteItems, products))
                .setItems(quoteItems);
        return ShippingQuoteResponse.from(shippingQuoteRepository.save(quote));
    }

    /** Non-binding basket preview uses catalog parcels/prices and never claims artwork or persists a quote. */
    @Transactional(readOnly = true)
    public List<ShippingEstimate> estimateBasket(ShippingBasketEstimateRequest request) {
        final String destination = normalizePostalCode(request == null ? null : request.zipCode());
        if (request.items() == null
                || request.items().isEmpty()
                || request.items().size() > MAX_ORDER_LINES) {
            throw new IllegalArgumentException("A shipping estimate requires 1 to 50 basket lines");
        }
        for (final ShippingBasketEstimateRequest.Item item : request.items()) {
            if (item == null
                    || item.productId() == null
                    || item.productId().isBlank()
                    || item.quantity() < 1
                    || item.quantity() > MAX_ITEM_QUANTITY) {
                throw new IllegalArgumentException("Invalid shipping estimate item");
            }
        }
        final Map<String, Product> products = loadProducts(request.items().stream()
                .map(ShippingBasketEstimateRequest.Item::productId)
                .distinct()
                .toList());
        final Map<String, Integer> quantities = new LinkedHashMap<>();
        final List<ShippingEstimateRequest> parcels = new ArrayList<>();
        BigDecimal itemAmount = ZERO;
        for (final ShippingBasketEstimateRequest.Item item : request.items()) {
            final Product product = products.get(item.productId());
            requireProductShippingData(product);
            final int quantity = quantities.getOrDefault(item.productId(), 0) + item.quantity();
            if (quantity > MAX_ITEM_QUANTITY)
                throw new IllegalArgumentException("Combined product quantity exceeds 100");
            quantities.put(item.productId(), quantity);
            if (item.personalized() && product.getAvailablePersonalizations().isEmpty()) {
                throw new IllegalArgumentException("This product cannot be personalized");
            }
            final BigDecimal basePrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            final BigDecimal unitPrice =
                    money(item.personalized() ? basePrice.add(personalizationSurcharge) : basePrice, "product price");
            itemAmount = itemAmount.add(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
            final Package packaging = product.getPackaging().orElseThrow();
            parcels.add(new ShippingEstimateRequest(
                    destination,
                    product.getWeightKg().floatValue(),
                    packaging.getDepth(),
                    packaging.getWidth(),
                    packaging.getHeight(),
                    item.quantity(),
                    unitPrice));
        }
        com.portcelana.natiart.service.support.DomainValidation.orderTotal(itemAmount);
        return shippingService.getShippingEstimates(parcels);
    }

    @Transactional(readOnly = true)
    public ShippingQuote requireQuoteForOrder(
            String quoteId,
            String ownerExternalId,
            String destinationPostalCode,
            List<OrderItemDto> orderItems,
            Map<String, Product> products) {
        requireOwner(ownerExternalId);
        if (quoteId == null || quoteId.isBlank()) {
            throw new ShippingQuoteNotValidException("A current shipping quote is required before payment");
        }
        final ShippingQuote quote = shippingQuoteRepository
                .findByIdAndOwnerExternalIdForUse(quoteId, ownerExternalId)
                .orElseThrow(
                        () -> new ShippingQuoteNotValidException("The shipping quote is not valid for this account"));
        final Instant now = clock.instant();
        if (quote.getExpiresAt() == null || !now.isBefore(quote.getExpiresAt())) {
            throw new ShippingQuoteNotValidException("The shipping quote has expired; request a new quote");
        }
        final String destination = normalizePostalCode(destinationPostalCode);
        final List<ShippingQuoteItem> currentItems = currentItems(orderItems, products);
        if (!Objects.equals(quote.getDestinationPostalCode(), destination)
                || !Objects.equals(quote.getRequestFingerprint(), fingerprint(destination, currentItems, products))) {
            throw new ShippingQuoteNotValidException(
                    "The shipping quote no longer matches the order; request a new quote");
        }
        return quote;
    }

    private Map<String, Product> loadProducts(List<String> productIds) {
        final Map<String, Product> products = productRepository.findAllWithShippingDataByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        for (String productId : productIds) {
            if (!products.containsKey(productId)) {
                throw new IllegalArgumentException("Product with id [" + productId + "] not found");
            }
        }
        return products;
    }

    private List<ShippingQuoteItemRequest> validateItems(List<ShippingQuoteItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A shipping quote must contain at least one item");
        }
        if (items.size() > MAX_ORDER_LINES) {
            throw new IllegalArgumentException(
                    "A shipping quote must not contain more than " + MAX_ORDER_LINES + " items");
        }
        for (ShippingQuoteItemRequest item : items) {
            if (item == null
                    || item.getProductId() == null
                    || item.getProductId().isBlank()) {
                throw new IllegalArgumentException("Every shipping quote item must reference a product");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > MAX_ITEM_QUANTITY) {
                throw new IllegalArgumentException(
                        "Shipping quote quantities must be between 1 and " + MAX_ITEM_QUANTITY);
            }
        }
        return items;
    }

    private List<ShippingQuoteItem> currentItems(List<OrderItemDto> orderItems, Map<String, Product> products) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new ShippingQuoteNotValidException("The shipping quote does not match an empty order");
        }
        final List<ShippingQuoteItem> currentItems = new ArrayList<>();
        final Set<String> lineIdentities = new HashSet<>();
        for (OrderItemDto item : orderItems) {
            if (item == null || item.getProductId() == null) {
                throw new ShippingQuoteNotValidException("The shipping quote does not match the order items");
            }
            final Product product = products.get(item.getProductId());
            if (product == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ShippingQuoteNotValidException("The shipping quote does not match the order items");
            }
            final Map<PersonalizationOption, String> options =
                    PersonalizationRules.validatedOptions(item.getPersonalization(), product);
            final String personalizationKey = PersonalizationRules.canonical(options);
            if (!lineIdentities.add(product.getId() + "\u0000" + personalizationKey)) {
                throw new ShippingQuoteNotValidException("The shipping quote does not match the order items");
            }
            final BigDecimal basePrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            final BigDecimal unitPrice = options.isEmpty() ? basePrice : basePrice.add(personalizationSurcharge);
            currentItems.add(new ShippingQuoteItem(
                    product.getId(),
                    personalizationKey,
                    item.getQuantity(),
                    money(unitPrice, "product price"),
                    product.getVersion()));
        }
        return currentItems;
    }

    private void requireProductShippingData(Product product) {
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
        }
        if (product.getCategory().map(category -> !category.isActive()).orElse(false)) {
            throw new IllegalArgumentException("Product category is no longer available");
        }
        com.portcelana.natiart.service.support.DomainValidation.weightKg(product.getWeightKg());
        final Package packaging = product.getPackaging().orElse(null);
        if (packaging == null || !packaging.isActive()) {
            throw new IllegalArgumentException("Product [" + product.getLabel() + "] has no active shipping package");
        }
    }

    private static String normalizePostalCode(String postalCode) {
        return com.portcelana.natiart.service.support.DomainValidation.cep(postalCode);
    }

    private static String fingerprint(
            String destination, List<ShippingQuoteItem> items, Map<String, Product> products) {
        final String canonicalItems = items.stream()
                .sorted(java.util.Comparator.comparing(ShippingQuoteItem::getProductId)
                        .thenComparing(ShippingQuoteItem::getPersonalizationKey))
                .map(item -> item.getProductId()
                        + ":"
                        + item.getPersonalizationKey()
                        + ":"
                        + item.getQuantity()
                        + ":"
                        + item.getUnitPrice().toPlainString()
                        + ":"
                        + item.getProductVersion()
                        + ":"
                        + shippingFingerprint(products.get(item.getProductId())))
                .collect(Collectors.joining("|"));
        final String value = destination + "|" + canonicalItems;
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private static String shippingFingerprint(Product product) {
        final Package packaging = product.getPackaging().orElse(null);
        if (packaging == null) return "missing-package";
        return product.isActive() + ":"
                + product.getWeightKg().stripTrailingZeros().toPlainString() + ":" + packaging.getId() + ":"
                + packaging.getVersion() + ":" + packaging.isActive() + ":" + packaging.getHeight() + ":"
                + packaging.getWidth() + ":" + packaging.getDepth();
    }

    private static BigDecimal money(BigDecimal amount, String field) {
        requireMoney(amount, field);
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireMoney(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "The " + field + " must be a non-negative amount with at most two decimals");
        }
    }

    private static void requireOwner(String ownerExternalId) {
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("A shipping quote must have an owner");
        }
    }
}
