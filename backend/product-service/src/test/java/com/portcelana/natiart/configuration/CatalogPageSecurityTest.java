package com.portcelana.natiart.configuration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import com.portcelana.natiart.controller.CategoryController;
import com.portcelana.natiart.controller.PackageController;
import com.portcelana.natiart.controller.ProductController;
import com.portcelana.natiart.dto.PagedResponseDto;
import com.portcelana.natiart.service.CategoryManager;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.PackageManager;
import com.portcelana.natiart.service.ProductManager;

@WebMvcTest(controllers = {ProductController.class, CategoryController.class, PackageController.class})
@Import(SecurityConfig.class)
class CatalogPageSecurityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.portcelana.natiart.service.RateLimitStore shippingRateLimitStore;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void configureSecurity() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @MockitoBean
    private ProductManager products;

    @MockitoBean
    private CategoryManager categories;

    @MockitoBean
    private PackageManager packages;

    @MockitoBean
    private ImageConversionService conversion;

    @MockitoBean
    private WebClient.Builder webClient;

    @MockitoBean
    private TokenValidationCache tokenCache;

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadInactiveAdminPages() throws Exception {
        for (final String resource : List.of("products", "categories", "packages")) {
            mvc.perform(get("/admin/" + resource + "/page")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(products, categories, packages);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMayReadAllThreeResourcePages() throws Exception {
        when(products.getProductsPage(isNull(), eq(""), any(), eq(true)))
                .thenReturn(new PagedResponseDto<>(List.of(), 0, 0, 20, false));
        when(categories.getCategoriesPage(any(), eq(true)))
                .thenReturn(new PagedResponseDto<>(List.of(), 0, 0, 20, false));
        when(packages.getPackagesPage(any(), eq(true))).thenReturn(new PagedResponseDto<>(List.of(), 0, 0, 20, false));
        for (final String resource : List.of("products", "categories", "packages")) {
            mvc.perform(get("/admin/" + resource + "/page")).andExpect(status().isOk());
        }
    }
}
