package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.portcelana.natiart.controller.OrderController;
import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.service.OrderManager;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, MvcConfig.class})
class OrderControllerSecurityTest {

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
    private OrderManager orderManager;

    @MockitoBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Test
    void createOrderRequiresFullAuthenticationLikeCartAndPayment() throws Exception {
        final Method createOrder = OrderController.class.getMethod(
                "createOrder", OrderDto.class, String.class, AuthenticationResponseDto.Principal.class);
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
    void authenticatedUserCanCreateOrder() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_MINE");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(orderManager.createOrder(any(OrderDto.class), any(), any()))
                .thenReturn(new CustomerOrder().setItems(List.of()));

        try {
            mockMvc.perform(post("/orders/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createOrderPassesAuthenticatedPrincipalExternalIdAsOwner() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_MINE");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(orderManager.createOrder(any(OrderDto.class), any(), any()))
                .thenReturn(new CustomerOrder().setItems(List.of()));

        try {
            mockMvc.perform(post("/orders/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(orderManager).createOrder(any(OrderDto.class), eq("cus_MINE"), isNull());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createOrderIgnoresClientSuppliedOwnerInBody() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_MINE");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(orderManager.createOrder(any(OrderDto.class), any(), any()))
                .thenReturn(new CustomerOrder().setItems(List.of()));

        try {
            mockMvc.perform(post("/orders/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ownerExternalId\":\"mallory\"}"))
                    .andExpect(status().isOk());

            verify(orderManager).createOrder(any(OrderDto.class), eq("cus_MINE"), isNull());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
