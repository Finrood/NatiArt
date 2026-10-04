package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.portcelana.natiart.configuration.ControllerAdvice;
import com.portcelana.natiart.controller.OrderController;
import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(
        properties = {
            "spring.sql.init.mode=never",
            "spring.jpa.open-in-view=false",
            "natiart.order.personalization-surcharge=2.50"
        })
@Import({
    OrderManagerImpl.class,
    OrderViewService.class,
    OrderCreationService.class,
    CustomerUploadService.class,
    ShippingQuoteService.class,
    ShippingPackingHttpIntegrationTest.CarrierConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShippingPackingHttpIntegrationTest {
    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderManager manager;

    @Autowired
    private OrderViewService views;

    @MockitoBean
    private AsaasChargeSafetyService chargeSafetyService;

    @MockitoBean
    private ProductManager productManager;

    @Autowired
    private ShippingQuoteService shipping;

    @Autowired
    private com.portcelana.natiart.repository.CustomerUploadRepository uploads;

    @MockitoBean
    private ImageConversionService imageConversion;

    @MockitoBean
    private com.portcelana.natiart.storage.StorageService storage;

    private MockMvc http;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setup() {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("owner");
        http = MockMvcBuilders.standaloneSetup(new OrderController(manager, views))
                .setControllerAdvice(new ControllerAdvice())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticationResponseDto.Principal.class;
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer container,
                            NativeWebRequest request,
                            WebDataBinderFactory binder) {
                        return principal;
                    }
                })
                .build();
    }

    @Autowired
    private org.springframework.web.client.RestTemplate carrierClient;

    @Autowired
    private com.portcelana.natiart.repository.PackageRepository packages;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class CarrierConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.web.client.RestTemplate carrierClient() {
            return new org.springframework.web.client.RestTemplate();
        }

        @org.springframework.context.annotation.Bean
        ShippingService shippingService(org.springframework.web.client.RestTemplate carrierClient) {
            return new ShippingService(
                    "https://carrier.example.test/calculate", "fixture-token", "88010000", carrierClient);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"one", "three", "variants", "custom"})
    void serializedCarrierMassMatchesUnitsAndConfirmedQuoteIsCommittedUnchanged(String scenario) throws Exception {
        final int units = (scenario.equals("one") || scenario.equals("custom")) ? 1 : 3;
        final Category category =
                categories.saveAndFlush(new Category(UUID.randomUUID().toString()));
        final com.portcelana.natiart.model.Package packaging = packages.saveAndFlush(
                new com.portcelana.natiart.model.Package("Unit parcel " + UUID.randomUUID(), 10, 15, 20));
        final Product product = products.saveAndFlush(new Product("Plate", BigDecimal.TEN)
                .setCategory(category)
                .setPackaging(packaging)
                .setWeightKg(new BigDecimal("0.50"))
                .setStockQuantity(10)
                .setAvailablePersonalizations(java.util.Set.of(
                        com.portcelana.natiart.model.support.PersonalizationOption.GOLDEN_BORDER,
                        com.portcelana.natiart.model.support.PersonalizationOption.CUSTOM_IMAGE)));
        when(productManager.getProductsOrDie(List.of(product.getId()))).thenReturn(Map.of(product.getId(), product));
        final org.springframework.test.web.client.MockRestServiceServer server =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(carrierClient)
                        .build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://carrier.example.test/calculate"))
                .andExpect(request -> {
                    final tools.jackson.databind.JsonNode body = json.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    final tools.jackson.databind.JsonNode volumes = body.get("volumes");
                    assertEquals(units, volumes.size());
                    double mass = 0;
                    for (int i = 0; i < volumes.size(); i++) {
                        final tools.jackson.databind.JsonNode volume = volumes.get(i);
                        org.junit.jupiter.api.Assertions.assertFalse(volume.has("qntd"));
                        for (String dimension : List.of("weight", "length", "width", "height"))
                            org.junit.jupiter.api.Assertions.assertTrue(
                                    volume.get(dimension).isNumber());
                        mass += volume.get("weight").asDouble();
                    }
                    assertEquals(0.5 * units, mass, 0.000001);
                })
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "[{\"id\":1,\"name\":\"PAC\",\"price\":7.50,\"delivery_time\":3,\"company\":{\"name\":\"Correios\"}}]",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        final java.util.List<com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest> quoteItems =
                new java.util.ArrayList<>();
        final java.util.List<OrderItemDto> items = new java.util.ArrayList<>();
        final com.portcelana.natiart.model.CustomerUpload artwork = scenario.equals("custom")
                ? uploads.saveAndFlush(new com.portcelana.natiart.model.CustomerUpload(
                        "owner", "file:customer-uploads/owned.webp", "image/webp", 12L))
                : null;
        if (scenario.equals("variants")) {
            final com.portcelana.natiart.dto.PersonalizationDto golden =
                    new com.portcelana.natiart.dto.PersonalizationDto()
                            .setPersonalizationOptions(Map.of(
                                    com.portcelana.natiart.model.support.PersonalizationOption.GOLDEN_BORDER, "true"));
            quoteItems.add(new com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest()
                    .setProductId(product.getId())
                    .setQuantity(1)
                    .setPersonalization(golden));
            items.add(new OrderItemDto()
                    .setProductId(product.getId())
                    .setQuantity(1)
                    .setPersonalization(golden));
            quoteItems.add(new com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest()
                    .setProductId(product.getId())
                    .setQuantity(2));
            items.add(new OrderItemDto().setProductId(product.getId()).setQuantity(2));
        } else if (artwork != null) {
            final com.portcelana.natiart.dto.PersonalizationDto custom =
                    new com.portcelana.natiart.dto.PersonalizationDto()
                            .setPersonalizationOptions(Map.of(
                                    com.portcelana.natiart.model.support.PersonalizationOption.CUSTOM_IMAGE,
                                    artwork.getId()));
            quoteItems.add(new com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest()
                    .setProductId(product.getId())
                    .setQuantity(1)
                    .setPersonalization(custom));
            items.add(new OrderItemDto()
                    .setProductId(product.getId())
                    .setQuantity(1)
                    .setPersonalization(custom));
        } else {
            quoteItems.add(new com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest()
                    .setProductId(product.getId())
                    .setQuantity(units));
            items.add(new OrderItemDto().setProductId(product.getId()).setQuantity(units));
        }
        final com.portcelana.natiart.dto.shipping.ShippingQuoteResponse quote = shipping.createQuote(
                new com.portcelana.natiart.dto.shipping.ShippingQuoteRequest()
                        .setZipCode("88010000")
                        .setItems(quoteItems),
                "owner");
        assertEquals(new BigDecimal("12.50"), quote.getShippingAmount());
        final OrderDto orderRequest = new OrderDto()
                .setFirstname("Buyer")
                .setHouseNumber("N/A")
                .setLastname("Customer")
                .setEmail("buyer@example.test")
                .setCountry("Brazil")
                .setState("SC")
                .setCity("City")
                .setNeighborhood("Area")
                .setStreet("Street")
                .setZipCode("88010000")
                .setItems(items)
                .setShippingQuoteId(quote.getQuoteId())
                .setDeliveryAmount(BigDecimal.ZERO);
        final String key = UUID.randomUUID().toString();
        final String body = json.writeValueAsString(orderRequest);
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAmount").value(12.5))
                .andExpect(
                        jsonPath("$.totalAmount").value(quote.getTotalAmount().doubleValue()));
        final com.portcelana.natiart.model.CustomerOrder saved =
                orders.findByOwnerExternalIdAndIdempotencyKey("owner", key).orElseThrow();
        assertEquals(quote.getShippingAmount(), saved.getDeliveryAmount());
        assertEquals(quote.getTotalAmount(), saved.getTotalAmount());
        assertEquals(quote.getQuoteId(), saved.getShippingQuoteId());
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()));
        assertEquals(
                10 - units, products.findById(product.getId()).orElseThrow().getStockQuantity());
        if (artwork != null) {
            assertEquals(product.getOriginalPrice().add(new BigDecimal("2.50")), quote.getItemAmount());
            org.junit.jupiter.api.Assertions.assertNotNull(
                    uploads.findById(artwork.getId()).orElseThrow().getConsumedAt());
            assertEquals(
                    artwork.getId(),
                    saved.getItems()
                            .getFirst()
                            .getPersonalization()
                            .getCustomImageUpload()
                            .getId());
        }
        server.verify();
    }
}
