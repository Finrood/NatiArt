package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.portcelana.natiart.controller.helper.ShippingQuoteNotValidException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.dto.PersonalizationDto;
import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteResponse;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.repository.ShippingQuoteRepository;

@ExtendWith(MockitoExtension.class)
class ShippingQuoteServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShippingQuoteRepository shippingQuoteRepository;

    @Mock
    private ShippingService shippingService;

    @Mock
    private CustomerUploadService customerUploadService;

    @Mock
    private ProductManager productManager;

    @Mock
    private OrderRepository orderRepository;

    private ShippingQuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new ShippingQuoteService(
                productRepository, shippingQuoteRepository, shippingService, Clock.fixed(NOW, ZoneOffset.UTC), 900);
    }

    @Test
    void createQuote_usesEveryProductLinePackageAndServerPrices() {
        final Product plate = product("plate", "Plate", "13.99", "0.35", new Package("small", 10, 15, 20));
        final Product vase = product("vase", "Vase", "22.99", "0.80", new Package("large", 30, 45, 60));
        when(productRepository.findAllWithShippingDataByIds(List.of("plate", "vase")))
                .thenReturn(List.of(plate, vase));
        final ShippingEstimate estimate = new ShippingEstimate()
                .setServiceId("correios-1")
                .setService("PAC")
                .setPrice(new BigDecimal("18.50"));
        final ArgumentCaptor<List<ShippingEstimateRequest>> volumes = ArgumentCaptor.forClass(List.class);
        when(shippingService.getShippingEstimates(anyList())).thenReturn(List.of(estimate));
        when(shippingQuoteRepository.save(org.mockito.ArgumentMatchers.any(ShippingQuote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final ShippingQuoteResponse response = quoteService.createQuote(
                new ShippingQuoteRequest()
                        .setZipCode("01001-000")
                        .setItems(List.of(
                                new ShippingQuoteItemRequest()
                                        .setProductId("plate")
                                        .setQuantity(2),
                                new ShippingQuoteItemRequest()
                                        .setProductId("vase")
                                        .setQuantity(1))),
                "owner-1");

        verify(shippingService).getShippingEstimates(volumes.capture());
        assertEquals(2, volumes.getValue().size());
        assertEquals(0.35f, volumes.getValue().get(0).getWeight());
        assertEquals(20.0f, volumes.getValue().get(0).getLength());
        assertEquals(2, volumes.getValue().get(0).getQuantity());
        assertEquals(60.0f, volumes.getValue().get(1).getLength());
        assertEquals(new BigDecimal("50.97"), response.getItemAmount());
        assertEquals(new BigDecimal("18.50"), response.getShippingAmount());
        assertEquals(new BigDecimal("69.47"), response.getTotalAmount());
        assertEquals(NOW.plusSeconds(900), response.getExpiresAt());
        assertEquals("correios-1", response.getServiceId());
    }

    @Test
    void twoPersonalizedVariantsShareFreightVolumeAndKeepQuotedLinePrices() {
        final Product plate = product("p1", "Plate", "10.00", "0.40", new Package("box", 10, 10, 10));
        plate.setAvailablePersonalizations(
                Set.of(PersonalizationOption.GOLDEN_BORDER, PersonalizationOption.CUSTOM_IMAGE));
        final String uploadId = "2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c";
        when(productRepository.findAllWithShippingDataByIds(List.of("p1"))).thenReturn(List.of(plate));
        when(shippingService.getShippingEstimates(anyList()))
                .thenReturn(List.of(new ShippingEstimate()
                        .setServiceId("pac")
                        .setService("PAC")
                        .setPrice(new BigDecimal("8.00"))));
        when(shippingQuoteRepository.save(org.mockito.ArgumentMatchers.any(ShippingQuote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        quoteService = new ShippingQuoteService(
                productRepository,
                shippingQuoteRepository,
                shippingService,
                customerUploadService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                900,
                new BigDecimal("2.50"));

        final ShippingQuoteResponse response = quoteService.createQuote(
                new ShippingQuoteRequest()
                        .setZipCode("01001000")
                        .setItems(List.of(
                                new ShippingQuoteItemRequest()
                                        .setProductId("p1")
                                        .setQuantity(1)
                                        .setPersonalization(
                                                personalization(PersonalizationOption.GOLDEN_BORDER, "true")),
                                new ShippingQuoteItemRequest()
                                        .setProductId("p1")
                                        .setQuantity(2)
                                        .setPersonalization(
                                                personalization(PersonalizationOption.CUSTOM_IMAGE, uploadId)))),
                "owner-1");

        final ArgumentCaptor<List<ShippingEstimateRequest>> volumes = ArgumentCaptor.forClass(List.class);
        verify(shippingService).getShippingEstimates(volumes.capture());
        assertEquals(1, volumes.getValue().size());
        assertEquals(3, volumes.getValue().getFirst().getQuantity());
        assertEquals(new BigDecimal("37.50"), response.getItemAmount());
        assertEquals(new BigDecimal("45.50"), response.getTotalAmount());
        assertEquals(2, response.getItems().size());
        assertEquals("GOLDEN_BORDER=true", response.getItems().getFirst().getPersonalizationKey());
        verify(customerUploadService).requireClaimableForQuote(uploadId, "owner-1");

        final ShippingQuote saved = capturedQuote();
        when(shippingQuoteRepository.findByIdAndOwnerExternalIdForUse(saved.getId(), "owner-1"))
                .thenReturn(Optional.of(saved));
        assertEquals(
                saved,
                quoteService.requireQuoteForOrder(
                        saved.getId(),
                        "owner-1",
                        "01001000",
                        List.of(
                                new OrderItemDto()
                                        .setProductId("p1")
                                        .setQuantity(2)
                                        .setPersonalization(
                                                personalization(PersonalizationOption.CUSTOM_IMAGE, uploadId)),
                                new OrderItemDto()
                                        .setProductId("p1")
                                        .setQuantity(1)
                                        .setPersonalization(
                                                personalization(PersonalizationOption.GOLDEN_BORDER, "true"))),
                        Map.of("p1", plate)));

        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", plate));
        when(productRepository.decreaseStockIfAvailable("p1", 3)).thenReturn(1);
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(customerUploadService.claimForOrder(uploadId, "owner-1"))
                .thenReturn(new CustomerUpload(uploadId, "owner-1", "file:customer-uploads/art.webp", "image/webp", 3));
        final CustomerOrder order = new OrderCreationService(
                        orderRepository,
                        productManager,
                        productRepository,
                        quoteService,
                        customerUploadService,
                        new BigDecimal("2.50"))
                .createOrder(
                        new OrderDto()
                                .setHouseNumber("N/A")
                                .setCountry("Brazil")
                                .setState("SP")
                                .setCity("City")
                                .setNeighborhood("Area")
                                .setStreet("Street")
                                .setFirstname("Ada")
                                .setLastname("Lovelace")
                                .setEmail("ada@example.test")
                                .setZipCode("01001000")
                                .setShippingQuoteId(saved.getId())
                                .setItems(List.of(
                                        new OrderItemDto()
                                                .setProductId("p1")
                                                .setQuantity(1)
                                                .setPersonalization(
                                                        personalization(PersonalizationOption.GOLDEN_BORDER, "true")),
                                        new OrderItemDto()
                                                .setProductId("p1")
                                                .setQuantity(2)
                                                .setPersonalization(personalization(
                                                        PersonalizationOption.CUSTOM_IMAGE, uploadId)))),
                        "owner-1",
                        "key-1",
                        "fingerprint");
        assertEquals(2, order.getItems().size());
        assertEquals(new BigDecimal("12.50"), order.getItems().getFirst().getPrice());
        assertEquals(new BigDecimal("45.50"), order.getTotalAmount());
        verify(productRepository).decreaseStockIfAvailable("p1", 3);
    }

    @Test
    void requireQuote_rejectsExpiredAndChangedOrderInsteadOfRepricingSilently() {
        final Product product = product("p1", "Product", "10.00", "0.40", new Package("box", 10, 10, 10));
        final ShippingQuoteResponse response = createQuote(product);
        final ShippingQuote persisted = new ShippingQuote()
                .setOwnerExternalId("owner-1")
                .setDestinationPostalCode("01001000")
                .setServiceId(response.getServiceId())
                .setServiceName(response.getServiceName())
                .setItemAmount(response.getItemAmount())
                .setShippingAmount(response.getShippingAmount())
                .setTotalAmount(response.getTotalAmount())
                .setExpiresAt(response.getExpiresAt())
                .setRequestFingerprint(capturedQuote().getRequestFingerprint())
                .setItems(capturedQuote().getItems());
        when(shippingQuoteRepository.findByIdAndOwnerExternalIdForUse("quote-1", "owner-1"))
                .thenReturn(Optional.of(persisted));

        assertEquals(
                persisted,
                quoteService.requireQuoteForOrder(
                        "quote-1",
                        "owner-1",
                        "01001000",
                        List.of(new OrderItemDto().setProductId("p1").setQuantity(1)),
                        Map.of("p1", product)));

        product.setMarkedPrice(new BigDecimal("11.00"));
        assertThrows(
                ShippingQuoteNotValidException.class,
                () -> quoteService.requireQuoteForOrder(
                        "quote-1",
                        "owner-1",
                        "01001000",
                        List.of(new OrderItemDto().setProductId("p1").setQuantity(1)),
                        Map.of("p1", product)));
    }

    @Test
    void requireQuote_rejectsQuoteOwnedByAnotherCustomer() {
        when(shippingQuoteRepository.findByIdAndOwnerExternalIdForUse("quote-1", "owner-2"))
                .thenReturn(Optional.empty());

        assertThrows(
                ShippingQuoteNotValidException.class,
                () -> quoteService.requireQuoteForOrder("quote-1", "owner-2", "01001000", List.of(), Map.of()));
    }

    private ShippingQuoteResponse createQuote(Product product) {
        when(productRepository.findAllWithShippingDataByIds(List.of("p1"))).thenReturn(List.of(product));
        when(shippingService.getShippingEstimates(anyList()))
                .thenReturn(List.of(new ShippingEstimate()
                        .setServiceId("service-1")
                        .setService("PAC")
                        .setPrice(new BigDecimal("8.00"))));
        when(shippingQuoteRepository.save(org.mockito.ArgumentMatchers.any(ShippingQuote.class)))
                .thenAnswer(invocation -> {
                    final ShippingQuote quote = invocation.getArgument(0);
                    quote.setItems(quote.getItems());
                    return quote;
                });
        return quoteService.createQuote(
                new ShippingQuoteRequest()
                        .setZipCode("01001000")
                        .setItems(List.of(new ShippingQuoteItemRequest()
                                .setProductId("p1")
                                .setQuantity(1))),
                "owner-1");
    }

    private ShippingQuote capturedQuote() {
        final ArgumentCaptor<ShippingQuote> captor = ArgumentCaptor.forClass(ShippingQuote.class);
        verify(shippingQuoteRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private Product product(String id, String label, String price, String weight, Package packaging) {
        final Product product = new Product(label, new BigDecimal(price))
                .setWeightKg(new BigDecimal(weight))
                .setPackaging(packaging);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private PersonalizationDto personalization(PersonalizationOption option, String value) {
        return new PersonalizationDto().setPersonalizationOptions(Map.of(option, value));
    }
}
