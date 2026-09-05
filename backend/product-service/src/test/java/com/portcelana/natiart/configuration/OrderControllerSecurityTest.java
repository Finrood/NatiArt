package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.portcelana.natiart.controller.OrderController;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.service.OrderManager;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, MvcConfig.class})
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderManager orderManager;

    @MockBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Test
    void createOrderRequiresFullAuthenticationLikeCartAndPayment() throws Exception {
        final Method createOrder = OrderController.class.getMethod("createOrder", OrderDto.class);
        final PreAuthorize preAuthorize = createOrder.getAnnotation(PreAuthorize.class);

        assertEquals("isFullyAuthenticated()", preAuthorize.value());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreateOrder() throws Exception {
        mockMvc.perform(post("/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) {
                        throw new AssertionError("Expected 401/403 for anonymous order creation but got " + s);
                    }
                });
    }

    @Test
    @WithMockUser(username = "jane")
    void authenticatedUserCanCreateOrder() throws Exception {
        when(orderManager.createOrder(any(OrderDto.class)))
                .thenReturn(new CustomerOrder().setItems(List.of()));

        mockMvc.perform(post("/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
