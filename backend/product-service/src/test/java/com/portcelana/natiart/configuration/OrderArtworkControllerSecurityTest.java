package com.portcelana.natiart.configuration;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

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

import com.portcelana.natiart.controller.OrderArtworkController;
import com.portcelana.natiart.service.OrderArtworkService;

@WebMvcTest(controllers = OrderArtworkController.class)
@Import({SecurityConfig.class, MvcConfig.class})
class OrderArtworkControllerSecurityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.portcelana.natiart.service.RateLimitStore shippingRateLimitStore;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @MockitoBean
    private OrderArtworkService artwork;

    @MockitoBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @MockitoBean
    private TokenValidationCache tokenValidationCache;

    private static final String URL = "/admin/orders/order-1/items/item-1/artwork";

    @Test
    void anonymousCannotReadArtwork() throws Exception {
        mvc.perform(get(URL)).andExpect(status().is4xxClientError());
        verifyNoInteractions(artwork);
    }

    @Test
    @WithMockUser(roles = "USER")
    void customerCannotReadFulfillmentArtwork() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(artwork);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminReceivesArtworkWithoutPublicCaching() throws Exception {
        when(artwork.openArtworkOrDie("order-1", "item-1")).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Type", "image/webp"));
    }
}
