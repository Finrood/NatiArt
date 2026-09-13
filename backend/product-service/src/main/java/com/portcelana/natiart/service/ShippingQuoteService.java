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
import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteResponse;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.ShippingQuoteItem;
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
    private final Clock clock;
    private final Duration quoteTtl;

    @Autowired
    public ShippingQuoteService(
            ProductRepository productRepository,
            ShippingQuoteRepository shippingQuoteRepository,
            ShippingService shippingService,
            @Value("${natiart.shipping.quote-ttl-seconds:900}") long quoteTtlSeconds) {
        this(productRepository, shippingQuoteRepository, shippingService, Clock.systemUTC(), quoteTtlSeconds);
    }

    ShippingQuoteService(
            ProductRepository productRepository,
            ShippingQuoteRepository shippingQuoteRepository,
            ShippingService shippingService,
            Clock clock,
            long quoteTtlSeconds) {
        if (quoteTtlSeconds < 1) {
            throw new IllegalArgumentException("Shipping quote TTL must be positive");
        }
        this.productRepository = productRepository;
        this.shippingQuoteRepository = shippingQuoteRepository;
        this.shippingService = shippingService;
        this.clock = clock;
        this.quoteTtl = Duration.ofSeconds(quoteTtlSeconds);
    }

    @Transactional
    public ShippingQuoteResponse createQuote(ShippingQuoteRequest request, String ownerExternalId) {
        requireOwner(ownerExternalId);
        final String destination = normalizePostalCode(request == null ? null : request.getZipCode());
        final List<ShippingQuoteItemRequest> requestedItems =
                validateItems(request == null ? null : request.getItems());
        final List<String> productIds = requestedItems.stream()
                .map(ShippingQuoteItemRequest::getProductId)
                .toList();
        final Map<String, Product> products = loadProducts(productIds);

        final List<ShippingQuoteItem> quoteItems = new ArrayList<>();
        final List<ShippingEstimateRequest> volumes = new ArrayList<>();
        BigDecimal itemAmount = ZERO;
        for (ShippingQuoteItemRequest requestedItem : requestedItems) {
            final Product product = products.get(requestedItem.getProductId());
            requireProductShippingData(product);
            final BigDecimal unitPrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            requireMoney(unitPrice, "product price");
            final int quantity = requestedItem.getQuantity();
            quoteItems.add(new ShippingQuoteItem(
                    product.getId(), quantity, money(unitPrice, "product price"), product.getVersion()));
            itemAmount = itemAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));

            final Package packaging = product.getPackaging()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product [" + product.getLabel() + "] has no shipping package configured"));
            volumes.add(new ShippingEstimateRequest(
                    destination,
                    product.getWeightKg().floatValue(),
                    packaging.getDepth(),
                    packaging.getWidth(),
                    packaging.getHeight(),
                    quantity));
        }

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
        final ShippingQuote quote = new ShippingQuote()
                .setOwnerExternalId(ownerExternalId)
                .setDestinationPostalCode(destination)
                .setServiceId(serviceId)
                .setServiceName(estimate.getService())
                .setItemAmount(normalizedItemAmount)
                .setShippingAmount(shippingAmount)
                .setTotalAmount(normalizedItemAmount.add(shippingAmount).setScale(2))
                .setExpiresAt(clock.instant().plus(quoteTtl))
                .setRequestFingerprint(fingerprint(destination, quoteItems))
                .setItems(quoteItems);
        return ShippingQuoteResponse.from(shippingQuoteRepository.save(quote));
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
                || !Objects.equals(quote.getRequestFingerprint(), fingerprint(destination, currentItems))) {
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
        final Set<String> productIds = new HashSet<>();
        for (ShippingQuoteItemRequest item : items) {
            if (item == null
                    || item.getProductId() == null
                    || item.getProductId().isBlank()) {
                throw new IllegalArgumentException("Every shipping quote item must reference a product");
            }
            if (!productIds.add(item.getProductId())) {
                throw new IllegalArgumentException("A shipping quote must not contain duplicate product lines");
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
        final Set<String> productIds = new HashSet<>();
        for (OrderItemDto item : orderItems) {
            if (item == null || item.getProductId() == null || !productIds.add(item.getProductId())) {
                throw new ShippingQuoteNotValidException("The shipping quote does not match the order items");
            }
            final Product product = products.get(item.getProductId());
            if (product == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ShippingQuoteNotValidException("The shipping quote does not match the order items");
            }
            final BigDecimal unitPrice = product.getMarkedPrice().orElseGet(product::getOriginalPrice);
            currentItems.add(new ShippingQuoteItem(
                    product.getId(), item.getQuantity(), money(unitPrice, "product price"), product.getVersion()));
        }
        return currentItems;
    }

    private void requireProductShippingData(Product product) {
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
        }
        if (product.getWeightKg() == null
                || product.getWeightKg().signum() <= 0
                || product.getWeightKg().compareTo(BigDecimal.valueOf(1000)) > 0
                || !Float.isFinite(product.getWeightKg().floatValue())) {
            throw new IllegalArgumentException("Product [" + product.getLabel() + "] has no valid shipping weight");
        }
        final Package packaging = product.getPackaging().orElse(null);
        if (packaging == null || !packaging.isActive()) {
            throw new IllegalArgumentException("Product [" + product.getLabel() + "] has no active shipping package");
        }
    }

    private static String normalizePostalCode(String postalCode) {
        final String normalized = postalCode == null ? "" : postalCode.replaceAll("\\D", "");
        if (!normalized.matches("\\d{8}")) {
            throw new IllegalArgumentException("Destination postal code must contain exactly eight digits");
        }
        return normalized;
    }

    private static String fingerprint(String destination, List<ShippingQuoteItem> items) {
        final String canonicalItems = items.stream()
                .sorted(java.util.Comparator.comparing(ShippingQuoteItem::getProductId))
                .map(item -> item.getProductId()
                        + ":"
                        + item.getQuantity()
                        + ":"
                        + item.getUnitPrice().toPlainString()
                        + ":"
                        + item.getProductVersion())
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
