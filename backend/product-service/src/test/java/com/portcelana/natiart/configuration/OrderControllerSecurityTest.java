package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.portcelana.natiart.service.OrderViewService;

@WebMvcTest(
        controllers = {
            OrderController.class,
            com.portcelana.natiart.controller.ShipmentController.class,
            com.portcelana.natiart.controller.OrderWorkspaceController.class,
            com.portcelana.natiart.controller.OrderNotificationController.class
        })
@Import({SecurityConfig.class, MvcConfig.class})
class OrderControllerSecurityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.portcelana.natiart.service.RateLimitStore shippingRateLimitStore;

    @MockitoBean
    private com.portcelana.natiart.service.ShipmentManager shipments;

    @MockitoBean
    private com.portcelana.natiart.service.OrderWorkspaceManager workspace;

    @MockitoBean
    private com.portcelana.natiart.service.OrderNotificationManager notifications;

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
    private OrderViewService orderViewService;

    @MockitoBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @MockitoBean
    private TokenValidationCache tokenValidationCache;

    @Test
    @WithAnonymousUser
    void anonymousCannotReadAnEmptyOrPopulatedOrderHistory() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(result -> {
            final int status = result.getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403);
        });
        org.mockito.Mockito.verifyNoInteractions(orderViewService, orderManager);
    }

    @Test
    void authenticatedNewAccountCanReadEmptyHistoryWithItsOwnMissingProviderIdentity() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(orderViewService.getCustomerOrders(null, 0, 20)).thenReturn(List.of());
        try {
            mockMvc.perform(get("/orders"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .json("[]"));
            verify(orderViewService).getCustomerOrders(null, 0, 20);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createOrderRequiresFullAuthenticationLikeCartAndPayment() throws Exception {
        final Method createOrder = OrderController.class.getMethod(
                "createOrder", OrderDto.class, String.class, AuthenticationResponseDto.Principal.class);
        final PreAuthorize preAuthorize = createOrder.getAnnotation(PreAuthorize.class);

        assertEquals("isFullyAuthenticated()", preAuthorize.value());
    }

    @Test
    void cancelOrderRequiresFullAuthentication() throws Exception {
        final Method cancelOrder =
                OrderController.class.getMethod("cancelOrder", String.class, AuthenticationResponseDto.Principal.class);
        final PreAuthorize preAuthorize = cancelOrder.getAnnotation(PreAuthorize.class);

        assertEquals("isFullyAuthenticated()", preAuthorize.value());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCancelOrder() throws Exception {
        mockMvc.perform(delete("/orders/order-1")).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s != 401 && s != 403) {
                throw new AssertionError("Expected 401/403 for anonymous order cancellation but got " + s);
            }
        });
    }

    @Test
    void authenticatedUserCancelsOrderWithPrincipalOwner() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_MINE");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(orderManager.cancelPendingOrderResponse("order-1", "cus_MINE"))
                .thenReturn(new OrderDto().setStatus(com.portcelana.natiart.model.support.OrderStatus.CANCELLED));

        try {
            mockMvc.perform(delete("/orders/order-1")).andExpect(status().isOk());
            verify(orderManager).cancelPendingOrderResponse("order-1", "cus_MINE");
        } finally {
            SecurityContextHolder.clearContext();
        }
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
            verify(orderViewService).getCustomerOrder(any(), eq("cus_MINE"));
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

    @Test
    void shopWorkspaceAndRecoveryAreAdministratorOnly() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "buyer", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            mockMvc.perform(get("/admin/order-workspace")).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/order-workspace/queue").param("status", "PAID"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/order-notifications/attention")).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/order-notifications/order:PAID/retry")).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/orders/order/shipment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"trackingCode\":\"BR123\"}"))
                    .andExpect(status().isForbidden());
            org.mockito.Mockito.verifyNoInteractions(shipments, workspace, notifications);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void administratorCanReadWorkspaceAndRequestSafeEmailRecovery() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "shop", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(workspace.overview()).thenReturn(new com.portcelana.natiart.dto.OrderWorkspaceDto(1, 2, 3, 4, 0));
        try {
            mockMvc.perform(get("/admin/order-workspace")).andExpect(status().isOk());
            mockMvc.perform(post("/admin/order-notifications/order:PAID/retry")).andExpect(status().isOk());
            verify(notifications).retry("order:PAID");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
