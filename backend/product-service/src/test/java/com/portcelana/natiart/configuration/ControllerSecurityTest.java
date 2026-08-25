package com.portcelana.natiart.configuration;

import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.portcelana.natiart.controller.ProductController.class,
        properties = "directory.service.url=http://localhost:8081")
@Import(SecurityConfig.class)
class ControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductManager productManager;

    @MockBean
    private ImageConversionService imageConversionService;

    @MockBean
    private WebClient.Builder webClientBuilder;

    private void expectForbiddenButNotAuthenticated(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s != 401 && s != 403) {
                throw new AssertionError("Expected 401/403 for unauthorized access but got " + s);
            }
        });
    }

    private void expectPassesSecurity(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) {
                throw new AssertionError("ADMIN role should pass the security layer but got " + s);
            }
        });
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreateProduct() throws Exception {
        expectForbiddenButNotAuthenticated(multipartPost());
    }

    private MockHttpServletRequestBuilder multipartPost() {
        MockMultipartFile productDto = new MockMultipartFile(
                "productDto", "productDto.json", "application/json", "{\"label\":\"x\"}".getBytes());
        return MockMvcRequestBuilders.multipart("/products/create").file(productDto);
    }

    @Test
    @WithMockUser(username = "customer", roles = {"USER"})
    void nonAdminCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/products/some-id")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminIsNotRejectedBySecurityOnDelete() throws Exception {
        expectPassesSecurity(delete("/products/some-id"));
    }

    @Test
    @WithAnonymousUser
    void anonymousCanReadProducts() throws Exception {
        mockMvc.perform(get("/products")).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) {
                throw new AssertionError("Public read endpoint must stay public but got " + s);
            }
        });
    }
}
