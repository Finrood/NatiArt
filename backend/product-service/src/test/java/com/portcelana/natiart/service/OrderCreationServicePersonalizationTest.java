package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.dto.PersonalizationDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class OrderCreationServicePersonalizationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductManager productManager;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShippingService shippingService;

    @Mock
    private CustomerUploadService customerUploadService;

    private OrderCreationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCreationService(
                orderRepository,
                productManager,
                productRepository,
                shippingService,
                customerUploadService,
                BigDecimal.ZERO);
        lenient().when(shippingService.getOrderShippingAmount(any())).thenReturn(BigDecimal.ZERO);
        lenient()
                .when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void distinctVariantsKeepSeparateLinesAndReserveAggregatedStock() {
        final Product product = product("p1");
        product.setAvailablePersonalizations(
                Set.of(PersonalizationOption.GOLDEN_BORDER, PersonalizationOption.CUSTOM_IMAGE));
        final String uploadId = "2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c";
        final CustomerUpload upload =
                new CustomerUpload(uploadId, "owner-1", "file:///tmp/customer.webp", "image/webp", 12L);
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", product));
        when(customerUploadService.claimForOrder(uploadId, "owner-1")).thenReturn(upload);
        when(productRepository.decreaseStockIfAvailable(product.getId(), 3)).thenReturn(1);

        final OrderDto order = validOrder()
                .setItems(List.of(
                        item("p1", 1, Map.of(PersonalizationOption.GOLDEN_BORDER, "true")),
                        item("p1", 2, Map.of(PersonalizationOption.CUSTOM_IMAGE, uploadId))));

        final CustomerOrder saved = service.createOrder(order, "owner-1", "key-1", "fingerprint");

        assertEquals(2, saved.getItems().size());
        assertEquals(1, saved.getItems().get(0).getQuantity());
        assertEquals(2, saved.getItems().get(1).getQuantity());
        assertEquals(
                "true",
                saved.getItems()
                        .get(0)
                        .getPersonalization()
                        .getPersonalizationOptions()
                        .get(PersonalizationOption.GOLDEN_BORDER));
        assertEquals(
                uploadId,
                saved.getItems()
                        .get(1)
                        .getPersonalization()
                        .getPersonalizationOptions()
                        .get(PersonalizationOption.CUSTOM_IMAGE));
        assertEquals(upload, saved.getItems().get(1).getPersonalization().getCustomImageUpload());
        assertEquals(
                uploadId,
                OrderItemDto.from(saved.getItems().get(1))
                        .getPersonalization()
                        .getPersonalizationOptions()
                        .get(PersonalizationOption.CUSTOM_IMAGE));
        verify(productRepository).decreaseStockIfAvailable(product.getId(), 3);
    }

    @Test
    void foreignArtworkIsRejectedBeforeStockReservation() {
        final Product product = product("p1");
        product.setAvailablePersonalizations(Set.of(PersonalizationOption.CUSTOM_IMAGE));
        final String uploadId = "2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c";
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", product));
        when(customerUploadService.claimForOrder(uploadId, "owner-1"))
                .thenThrow(new ResourceNotFoundException("Custom artwork upload was not found"));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.createOrder(
                        validOrder()
                                .setItems(List.of(item("p1", 1, Map.of(PersonalizationOption.CUSTOM_IMAGE, uploadId)))),
                        "owner-1",
                        "key-1",
                        "fingerprint"));
        verify(productRepository, never()).decreaseStockIfAvailable(anyString(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void quantityCapAppliesAcrossDistinctFulfillmentVariants() {
        final String uploadId = "2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c";
        final OrderDto order = validOrder()
                .setItems(List.of(
                        item("p1", 60, Map.of(PersonalizationOption.GOLDEN_BORDER, "true")),
                        item("p1", 41, Map.of(PersonalizationOption.CUSTOM_IMAGE, uploadId))));

        assertThrows(IllegalArgumentException.class, () -> service.createOrder(order, "owner-1", null, "fingerprint"));
        verify(productManager, never()).getProductsOrDie(any());
        verify(productRepository, never()).decreaseStockIfAvailable(anyString(), anyInt());
    }

    @Test
    void unsupportedOptionIsRejectedWithoutClaimingArtwork() {
        final Product product = product("p1");
        product.setAvailablePersonalizations(Set.of());
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", product));
        final String uploadId = "2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c";

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrder(
                        validOrder()
                                .setItems(List.of(item("p1", 1, Map.of(PersonalizationOption.CUSTOM_IMAGE, uploadId)))),
                        "owner-1",
                        null,
                        "fingerprint"));
        verify(customerUploadService, never()).claimForOrder(anyString(), anyString());
        verify(productRepository, never()).decreaseStockIfAvailable(anyString(), anyInt());
    }

    @Test
    void personalizationSurchargeIsAddedFromServerPolicy() {
        final Product product = product("p1");
        product.setAvailablePersonalizations(Set.of(PersonalizationOption.GOLDEN_BORDER));
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", product));
        when(productRepository.decreaseStockIfAvailable(product.getId(), 2)).thenReturn(1);
        service = new OrderCreationService(
                orderRepository,
                productManager,
                productRepository,
                shippingService,
                customerUploadService,
                new BigDecimal("2.50"));

        final CustomerOrder saved = service.createOrder(
                validOrder().setItems(List.of(item("p1", 2, Map.of(PersonalizationOption.GOLDEN_BORDER, "true")))),
                "owner-1",
                null,
                "fingerprint");

        assertEquals(new BigDecimal("10.50"), saved.getItems().getFirst().getPrice());
        assertEquals(new BigDecimal("21.00"), saved.getTotalAmount());
    }

    private Product product(String id) {
        return new Product("Product " + id, new BigDecimal("10.00")).setMarkedPrice(new BigDecimal("8.00"));
    }

    private OrderItemDto item(String productId, int quantity, Map<PersonalizationOption, String> options) {
        return new OrderItemDto()
                .setProductId(productId)
                .setQuantity(quantity)
                .setPersonalization(new PersonalizationDto().setPersonalizationOptions(options));
    }

    private OrderDto validOrder() {
        return new OrderDto().setFirstname("Ada").setLastname("Lovelace").setEmail("ada@example.test");
    }
}
