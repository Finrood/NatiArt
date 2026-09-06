package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

import com.portcelana.natiart.controller.CartController;
import com.portcelana.natiart.service.CartManager;

/**
 * The product-service filter chain must be stateless like its directory twin:
 * JWT-authenticated traffic mints no server-side HTTP session.
 */
@WebMvcTest(controllers = CartController.class, properties = "directory.service.url=http://localhost:8081")
@Import({SecurityConfig.class, MvcConfig.class})
class StatelessSessionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartManager cartManager;

    @MockBean
    private WebClient.Builder webClientBuilder;

    @Test
    @WithMockUser(username = "jane")
    void authenticatedRequestCreatesNoSession() throws Exception {
        when(cartManager.getCartItemsByUsername(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/cart"))
                .andExpect(result -> assertNull(
                        result.getRequest().getSession(false),
                        "Stateless chain must not create an HTTP session for authenticated traffic"));
    }
}
