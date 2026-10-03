package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.params.provider.NullSource;
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
@Import({OrderManagerImpl.class, OrderCreationService.class, OrderViewService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderTextBoundaryIntegrationTest {
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
    private ProductManager productManager;

    @MockitoBean
    private ShippingQuoteService shipping;

    @MockitoBean
    private CustomerUploadService customerUploads;

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

    private OrderDto request(String number) {
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
                .setHouseNumber(number)
                .setItems(List.of(new OrderItemDto()
                        .setProductId("not-read-before-validation")
                        .setQuantity(1)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "over-limit"})
    void invalidHouseNumberIsFieldErrorBeforeEgressOrWrites(String value) throws Exception {
        final String number = "over-limit".equals(value) ? "a".repeat(256) : value;
        final long before = orders.count();
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(json.writeValueAsString(request(number))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("houseNumber"));
        assertEquals(before, orders.count());
        verifyNoInteractions(shipping, productManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"N/A", "maximum", "padded"})
    void normalizedHouseNumberRoundTripsAfterCommittedHttpOrder(String value) throws Exception {
        final String expected = "N/A".equals(value) ? value : "a".repeat(255);
        final String submitted = "padded".equals(value) ? " " + expected + " " : expected;
        final Category category =
                categories.saveAndFlush(new Category(UUID.randomUUID().toString()));
        final Product product = products.saveAndFlush(new Product("Plate", BigDecimal.TEN)
                .setCategory(category)
                .setMarkedPrice(BigDecimal.TEN)
                .setStockQuantity(2));
        when(productManager.getProductsOrDie(List.of(product.getId()))).thenReturn(Map.of(product.getId(), product));
        when(shipping.requireQuoteForOrder(any(), any(), any(), any(), any()))
                .thenReturn(new com.portcelana.natiart.model.ShippingQuote()
                        .setShippingAmount(BigDecimal.ZERO)
                        .setItemAmount(BigDecimal.TEN)
                        .setTotalAmount(BigDecimal.TEN)
                        .setItems(List.of(new com.portcelana.natiart.model.ShippingQuoteItem(
                                product.getId(), 1, BigDecimal.TEN, 0))));
        final OrderDto request = request(submitted)
                .setItems(
                        List.of(new OrderItemDto().setProductId(product.getId()).setQuantity(1)));
        final String key = UUID.randomUUID().toString();
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").value(expected));
        assertEquals(
                expected,
                orders.findByOwnerExternalIdAndIdempotencyKey("owner", key)
                        .orElseThrow()
                        .getHouseNumber());
        // The same normalized snapshot replays without a second stock reservation.
        request.setHouseNumber(expected);
        http.perform(post("/orders/create")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
        assertEquals(1, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }
}
