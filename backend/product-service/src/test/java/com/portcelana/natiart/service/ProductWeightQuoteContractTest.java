package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.dto.shipping.ShippingQuoteItemRequest;
import com.portcelana.natiart.dto.shipping.ShippingQuoteRequest;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.repository.CartItemRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.repository.ShippingQuoteRepository;
import com.portcelana.natiart.storage.StorageService;

import tools.jackson.databind.json.JsonMapper;

class ProductWeightQuoteContractTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryManager categories = mock(CategoryManager.class);
    private final PackageManager packages = mock(PackageManager.class);
    private final ProductManagerImpl catalog = new ProductManagerImpl(
            products,
            mock(OrderRepository.class),
            mock(CartItemRepository.class),
            categories,
            packages,
            mock(StorageService.class),
            mock(ProductImageLifecycle.class));

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.011", "99.999", "100"})
    void acceptedCatalogWeightReachesActualQuoteAndProviderVolumeUnchanged(String weight) {
        when(categories.getCategoryOrDie("category")).thenReturn(new Category("Art"));
        when(packages.getPackage("package")).thenReturn(Optional.of(new Package("Box", 10, 10, 10)));
        when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));
        final ProductDto dto = dto(weight);
        final Product created = catalog.createProduct(dto, List.of());
        dto.setId(created.getId());
        when(products.findById(created.getId())).thenReturn(Optional.of(created));
        final Product updated = catalog.updateProduct(dto, List.of());
        when(products.findAllWithShippingDataByIds(List.of(updated.getId()))).thenReturn(List.of(updated));
        final RestTemplate transport = new RestTemplate();
        final MockRestServiceServer provider =
                MockRestServiceServer.bindTo(transport).build();
        provider.expect(requestTo("https://carrier.example.test/quote"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    final String body = ((MockClientHttpRequest) request).getBodyAsString();
                    final var volumes =
                            JsonMapper.builder().build().readTree(body).get("volumes");
                    assertEquals(2, volumes.size());
                    for (final var volume : volumes) {
                        assertTrue(volume.get("weight").isNumber());
                        assertEquals(
                                Double.parseDouble(weight), volume.get("weight").asDouble(), 0.00001);
                    }
                })
                .andRespond(withSuccess("""
                    [{"id":1,"name":"PAC","price":"12.00","delivery_time":3,"company":{"name":"Correios"}}]
                    """, MediaType.APPLICATION_JSON));
        final ShippingQuoteRepository quotes = mock(ShippingQuoteRepository.class);
        when(quotes.save(any(ShippingQuote.class))).thenAnswer(call -> call.getArgument(0));
        final ShippingQuoteService shipping = new ShippingQuoteService(
                products,
                quotes,
                new ShippingService("https://carrier.example.test/quote", "inert-key", "01001000", transport),
                Clock.systemUTC(),
                900);
        final var quote = shipping.createQuote(
                new ShippingQuoteRequest()
                        .setZipCode("01001000")
                        .setItems(List.of(new ShippingQuoteItemRequest()
                                .setProductId(updated.getId())
                                .setQuantity(2))),
                "owner");
        assertEquals(new BigDecimal("20.00"), quote.getItemAmount());
        provider.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.005", "0.009", "100.001", "1000", "0.0101"})
    void unsupportedWeightsCannotEnterCatalogOrReachCarrier(String weight) {
        assertThrows(IllegalArgumentException.class, () -> catalog.createProduct(dto(weight), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.updateProduct(dto(weight).setId("existing"), List.of()));
        verifyNoInteractions(products, categories, packages);
        final Product legacy = new Product("Legacy", BigDecimal.TEN)
                .setWeightKg(new BigDecimal(weight))
                .setPackaging(new Package("Box", 10, 10, 10));
        when(products.findAllWithShippingDataByIds(List.of(legacy.getId()))).thenReturn(List.of(legacy));
        final ShippingService carrier = mock(ShippingService.class);
        final ShippingQuoteRepository quotes = mock(ShippingQuoteRepository.class);
        final ShippingQuoteService shipping =
                new ShippingQuoteService(products, quotes, carrier, Clock.systemUTC(), 900);
        assertThrows(
                IllegalArgumentException.class,
                () -> shipping.createQuote(
                        new ShippingQuoteRequest()
                                .setZipCode("01001000")
                                .setItems(List.of(new ShippingQuoteItemRequest()
                                        .setProductId(legacy.getId())
                                        .setQuantity(1))),
                        "owner"));
        verifyNoInteractions(carrier, quotes);
    }

    private ProductDto dto(String weight) {
        return new ProductDto("Art", BigDecimal.TEN)
                .setWeightKg(new BigDecimal(weight))
                .setCategoryId("category")
                .setPackageId("package")
                .setStockQuantity(10);
    }
}
