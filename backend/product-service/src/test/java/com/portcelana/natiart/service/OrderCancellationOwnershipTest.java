package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.configuration.ControllerAdvice;
import com.portcelana.natiart.controller.OrderController;
import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.ProductRepository;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({OrderManagerImpl.class, OrderController.class, OrderCancellationOwnershipTest.MethodSecurity.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCancellationOwnershipTest {
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired
    private OrderController controller;

    @Autowired
    private OrderManager manager;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private OrderCreationService creation;

    @MockitoBean
    private OrderViewService views;

    @MockitoBean
    private AsaasChargeSafetyService chargeSafety;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("deniedCustomers")
    void customerCancellationRejectsMissingOrForeignOwnerWithoutMutation(
            String requesterExternalId, boolean withPayment, boolean missingPrincipal) throws Exception {
        final String[] ids = seed(withPayment);
        authenticate(requesterExternalId, missingPrincipal);
        final MockHttpServletResponse response =
                http().perform(delete("/orders/" + ids[0])).andReturn().getResponse();

        assertEquals(403, response.getStatus());
        assertFalse(response.getContentAsString().contains("victim@example.test"));
        assertFalse(response.getContentAsString().contains(ids[0]));
        assertFalse(response.getContentAsString().contains("Victim"));
        assertEquals(OrderStatus.PENDING, orders.findById(ids[0]).orElseThrow().getStatus());
        assertEquals(8, products.findById(ids[1]).orElseThrow().getStockQuantity());
        assertEquals(withPayment, payments.findByOrderId(ids[0]).isPresent());
        verifyNoInteractions(chargeSafety);
    }

    static Stream<Arguments> deniedCustomers() {
        return Stream.of(false, true)
                .flatMap(withPayment -> Stream.of(
                        Arguments.of(null, withPayment, false),
                        Arguments.of("", withPayment, false),
                        Arguments.of(" \t", withPayment, false),
                        Arguments.of("cus_ATTACKER", withPayment, false),
                        Arguments.of(null, withPayment, true)));
    }

    @Test
    void ownerCancelsThroughHttpAndReplayDoesNotRestoreStockTwice() throws Exception {
        final String[] ids = seed(false);
        authenticate("cus_VICTIM", false);
        for (int attempt = 0; attempt < 2; attempt++) {
            final MockHttpServletResponse response =
                    http().perform(delete("/orders/" + ids[0])).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            assertEquals(
                    OrderStatus.CANCELLED, orders.findById(ids[0]).orElseThrow().getStatus());
            assertEquals(10, products.findById(ids[1]).orElseThrow().getStockQuantity());
        }
    }

    @Test
    void internalCancellationDoesNotRequireCustomerAuthenticationAndReleasesOnce() {
        final String[] ids = seed(false);
        manager.cancelPendingOrderInternally(ids[0]);
        manager.cancelPendingOrderInternally(ids[0]);
        assertEquals(
                OrderStatus.CANCELLED, orders.findById(ids[0]).orElseThrow().getStatus());
        assertEquals(10, products.findById(ids[1]).orElseThrow().getStockQuantity());
    }

    @Test
    void administrativeLifecycleRetainsSafePendingCancellation() {
        final String[] ids = seed(false);
        manager.updateOrderStatus(ids[0], OrderStatus.CANCELLED);
        assertEquals(
                OrderStatus.CANCELLED, orders.findById(ids[0]).orElseThrow().getStatus());
        assertEquals(10, products.findById(ids[1]).orElseThrow().getStockQuantity());
    }

    private String[] seed(boolean withPayment) {
        final String[] ids = new TransactionTemplate(transactions).execute(status -> {
            final Category category = categories.save(new Category("ownership-probe-" + System.nanoTime()));
            final Product product = products.save(new Product("Victim artwork", BigDecimal.TEN)
                    .setCategory(category)
                    .setStockQuantity(8));
            final CustomerOrder order = new CustomerOrder()
                    .setFirstname("Victim")
                    .setLastname("Customer")
                    .setEmail("victim@example.test")
                    .setOwnerExternalId("cus_VICTIM")
                    .setOrderDate(Instant.now())
                    .setStatus(OrderStatus.PENDING)
                    .setDeliveryAmount(BigDecimal.ZERO)
                    .setTotalAmount(new BigDecimal("20.00"));
            order.addOrderItem(
                    new CustomerOrderItem().setProduct(product).setQuantity(2).setPrice(BigDecimal.TEN));
            orders.saveAndFlush(order);
            if (withPayment) {
                payments.saveAndFlush(
                        new Payment("pay_" + order.getId(), "cus_VICTIM", order.getId(), "key_" + order.getId()));
            }
            return new String[] {order.getId(), product.getId()};
        });
        return ids;
    }

    private void authenticate(String requesterExternalId, boolean missingPrincipal) {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        if (!missingPrincipal) {
            when(principal.getExternalId()).thenReturn(requesterExternalId);
        }
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        missingPrincipal ? "authenticated-without-customer-principal" : principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private MockMvc http() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }
}
