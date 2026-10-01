package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.jpa.open-in-view=false"})
@Import({OrderManagerImpl.class, OrderCreationService.class, CustomerUploadService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ArtworkRecoveryHttpIntegrationTest {
    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderManager manager;

    @MockitoBean
    private ProductManager productManager;

    @MockitoBean
    private ShippingService shipping;

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
        http = MockMvcBuilders.standaloneSetup(new OrderController(manager))
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

    private Product product() {
        final Category category =
                categories.saveAndFlush(new Category(UUID.randomUUID().toString()));
        final Product product = products.saveAndFlush(new Product("Plate", BigDecimal.TEN)
                .setCategory(category)
                .setStockQuantity(2)
                .setAvailablePersonalizations(
                        java.util.Set.of(com.portcelana.natiart.model.support.PersonalizationOption.CUSTOM_IMAGE)));
        when(productManager.getProductsOrDie(List.of(product.getId()))).thenReturn(Map.of(product.getId(), product));
        when(shipping.getOrderShippingAmount(any())).thenReturn(BigDecimal.ZERO);
        return product;
    }

    private OrderDto request(Product product, String uploadId) {
        return new OrderDto()
                .setFirstname("Buyer")
                .setLastname("Customer")
                .setEmail("buyer@example.test")
                .setCountry("Brazil")
                .setState("SC")
                .setCity("City")
                .setNeighborhood("Area")
                .setStreet("Street")
                .setZipCode("88010000")
                .setItems(List.of(new OrderItemDto()
                        .setProductId(product.getId())
                        .setQuantity(1)
                        .setPersonalization(new com.portcelana.natiart.dto.PersonalizationDto()
                                .setPersonalizationOptions(Map.of(
                                        com.portcelana.natiart.model.support.PersonalizationOption.CUSTOM_IMAGE,
                                        uploadId)))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "expired", "consumed"})
    void definitivelyUnusableArtworkRollsBackOrderAndStockWithTypedRecoveryResponse(String reason) throws Exception {
        final Product product = product();
        final String uploadId = UUID.randomUUID().toString();
        if (!reason.equals("missing")) {
            final com.portcelana.natiart.model.CustomerUpload upload = new com.portcelana.natiart.model.CustomerUpload(
                    uploadId, "owner", "file:customer-uploads/owned.webp", "image/webp", 12L);
            if (reason.equals("expired"))
                org.springframework.test.util.ReflectionTestUtils.setField(
                        upload, "createdAt", java.time.Instant.now().minus(java.time.Duration.ofDays(2)));
            else upload.setConsumedAt(java.time.Instant.now());
            uploads.saveAndFlush(upload);
        }
        final long before = orders.count();
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(json.writeValueAsString(request(product, uploadId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUSTOM_ARTWORK_UNAVAILABLE"))
                .andExpect(jsonPath("$.uploadId").value(uploadId))
                .andExpect(jsonPath("$.orderCreated").value(false));
        assertEquals(before, orders.count());
        assertEquals(2, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @org.junit.jupiter.api.Test
    void alreadyClaimedArtworkReplaysItsCommittedOrderWithoutAnotherClaimOrReservation() throws Exception {
        final Product product = product();
        final com.portcelana.natiart.model.CustomerUpload upload =
                uploads.saveAndFlush(new com.portcelana.natiart.model.CustomerUpload(
                        "owner", "file:customer-uploads/owned.webp", "image/webp", 12L));
        final String key = UUID.randomUUID().toString();
        final String body = json.writeValueAsString(request(product, upload.getId()));
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
        final java.time.Instant claimed =
                uploads.findById(upload.getId()).orElseThrow().getConsumedAt();
        org.junit.jupiter.api.Assertions.assertNotNull(claimed);
        final String orderId = orders.findByOwnerExternalIdAndIdempotencyKey("owner", key)
                .orElseThrow()
                .getId();
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
        assertEquals(claimed, uploads.findById(upload.getId()).orElseThrow().getConsumedAt());
        assertEquals(1, products.findById(product.getId()).orElseThrow().getStockQuantity());
        org.mockito.Mockito.verify(shipping, org.mockito.Mockito.times(1)).getOrderShippingAmount(any());
    }
}
