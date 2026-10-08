package com.portcelana.natiart.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.service.CartManager;
import com.portcelana.natiart.service.CategoryManager;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.PackageManager;
import com.portcelana.natiart.service.ProductManager;

@WebMvcTest(
        controllers = {
            com.portcelana.natiart.controller.ProductController.class,
            com.portcelana.natiart.controller.CategoryController.class,
            com.portcelana.natiart.controller.PackageController.class,
            com.portcelana.natiart.controller.CartController.class
        },
        properties = "directory.service.url=http://localhost:8081")
@Import({SecurityConfig.class, MvcConfig.class})
class ControllerSecurityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.portcelana.natiart.service.RateLimitStore shippingRateLimitStore;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @MockitoBean
    private ProductManager productManager;

    @MockitoBean
    private ImageConversionService imageConversionService;

    @MockitoBean
    private CategoryManager categoryManager;

    @MockitoBean
    private PackageManager packageManager;

    @MockitoBean
    private CartManager cartManager;

    @MockitoBean
    private WebClient.Builder webClientBuilder;

    @MockitoBean
    private TokenValidationCache tokenValidationCache;

    private void expectForbiddenButNotAuthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s != 401 && s != 403) {
                throw new AssertionError("Expected 401/403 for unauthorized access but got " + s);
            }
        });
    }

    private void expectPassesSecurity(RequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) {
                throw new AssertionError("Authorized role should pass the security layer but got " + s);
            }
            if (s >= 500) {
                throw new AssertionError("Authorized request should not reach a 5xx error but got " + s);
            }
        });
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreateProduct() throws Exception {
        expectForbiddenButNotAuthenticated(multipartPost());
    }

    private RequestBuilder multipartPost() {
        MockMultipartFile productDto = new MockMultipartFile(
                "productDto", "productDto.json", "application/json", "{\"label\":\"x\"}".getBytes());
        return MockMvcRequestBuilders.multipart("/products/create").file(productDto);
    }

    @Test
    @WithMockUser(
            username = "customer",
            roles = {"USER"})
    void nonAdminCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/products/some-id")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(
            username = "admin",
            roles = {"ADMIN"})
    void adminIsNotRejectedBySecurityOnDelete() throws Exception {
        expectPassesSecurity(delete("/products/some-id"));
    }

    @Test
    @WithAnonymousUser
    void anonymousCanReadProducts() throws Exception {
        mockMvc.perform(get("/products")).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403 || s >= 500) {
                throw new AssertionError("Public read endpoint must stay public (no 401/403/5xx) but got " + s
                        + " resolved=" + result.getResolvedException());
            }
        });
    }

    @Test
    @WithAnonymousUser
    void anonymousProductListingServesCatalogWithoutUserResolution() throws Exception {
        // The listing is intentionally public and takes no user parameter:
        // an empty catalog must render 200 with no security rejection.
        when(productManager.getActiveProducts(any())).thenReturn(List.of());

        mockMvc.perform(get("/products")).andExpect(status().isOk());

        verify(productManager).getActiveProducts(any());
    }

    @Test
    @WithAnonymousUser
    void anonymousCategoryListingOnlyUsesPublicCategories() throws Exception {
        when(categoryManager.getActiveCategories(any())).thenReturn(List.of(new Category("Visible")));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Visible"));

        verify(categoryManager).getActiveCategories(any());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void adminCategoryListingCanFindHiddenCategories() throws Exception {
        when(categoryManager.getCategories(any())).thenReturn(List.of(new Category("Hidden").setActive(false)));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));

        verify(categoryManager).getCategories(any());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadHiddenCategoryOrItsProductList() throws Exception {
        when(categoryManager.getActiveCategoryOrDie("hidden"))
                .thenThrow(new ResourceNotFoundException("Category with id hidden not found"));

        mockMvc.perform(get("/categories/hidden")).andExpect(status().isNotFound());
        mockMvc.perform(get("/products").param("categoryId", "hidden")).andExpect(status().isNotFound());

        verify(categoryManager, times(2)).getActiveCategoryOrDie("hidden");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void adminCanReadHiddenCategoryAndItsProductList() throws Exception {
        final Category hidden = new Category("Hidden").setActive(false);
        when(categoryManager.getCategoryOrDie("hidden")).thenReturn(hidden);
        when(productManager.getProductsByCategory(eq(hidden), any())).thenReturn(List.of());

        mockMvc.perform(get("/categories/hidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/products").param("categoryId", "hidden")).andExpect(status().isOk());

        verify(categoryManager, times(2)).getCategoryOrDie("hidden");
        verify(productManager).getProductsByCategory(eq(hidden), any());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreateCategory() throws Exception {
        expectForbiddenButNotAuthenticated(post("/categories/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Test
    @WithMockUser(
            username = "customer",
            roles = {"USER"})
    void nonAdminCannotUpdateCategory() throws Exception {
        mockMvc.perform(put("/categories/cat-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(
            username = "customer",
            roles = {"USER"})
    void nonAdminCannotInverseCategoryVisibility() throws Exception {
        mockMvc.perform(patch("/categories/cat-1/visibility/inverse")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(
            username = "admin",
            roles = {"ADMIN"})
    void adminIsNotRejectedBySecurityOnCategoryDelete() throws Exception {
        expectPassesSecurity(delete("/categories/cat-1"));
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreatePackage() throws Exception {
        expectForbiddenButNotAuthenticated(
                post("/packages/create").contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    @Test
    @WithMockUser(
            username = "customer",
            roles = {"USER"})
    void nonAdminCannotDeletePackage() throws Exception {
        mockMvc.perform(delete("/packages/pkg-1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(
            username = "admin",
            roles = {"ADMIN"})
    void adminIsNotRejectedBySecurityOnPackageDelete() throws Exception {
        expectPassesSecurity(delete("/packages/pkg-1"));
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadCart() throws Exception {
        expectForbiddenButNotAuthenticated(get("/cart"));
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotAddToCart() throws Exception {
        expectForbiddenButNotAuthenticated(post("/cart/item/p1/add"));
    }

    @Test
    @WithMockUser(username = "jane")
    void authenticatedUserCanAddToCart() throws Exception {
        expectPassesSecurity(post("/cart/item/p1/add"));
    }
}
