package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;
import com.portcelana.natiart.service.PaymentService;

@WebMvcTest(controllers = com.portcelana.natiart.controller.PaymentController.class)
@Import({SecurityConfig.class, MvcConfig.class})
class PaymentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Test
    @WithAnonymousUser
    void anonymousCannotReadPaymentStatus() throws Exception {
        // isFullyAuthenticated() must reject the anonymous principal with 401/403 (not 500).
        mockMvc.perform(get("/api/payment/pay-1/status")).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s != 401 && s != 403) {
                throw new AssertionError("Expected 401/403 for anonymous payment status but got " + s);
            }
        });
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadPixQrCode() throws Exception {
        mockMvc.perform(get("/api/payment/pay-1/pixQrCode")).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s != 401 && s != 403) {
                throw new AssertionError("Expected 401/403 for anonymous PIX QR but got " + s);
            }
        });
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotCreatePayment() throws Exception {
        mockMvc.perform(
                        post("/api/payment/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"paymentProcessor\":\"ASAAS\",\"customerId\":\"cus_OTHER\",\"value\":10.0,\"billingType\":\"PIX\"}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) {
                        throw new AssertionError("Expected 401/403 for anonymous payment creation but got " + s);
                    }
                });
    }

    @Test
    void authenticatedCallerSeesOwnedPaymentStatusAndCallerExternalIdIsPassed() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_MINE");
        final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(paymentService.getPaymentStatus("pay-1", "cus_MINE"))
                .thenReturn(new PaymentStatusResponse("pay-1", PaymentStatus.PENDING));

        try {
            mockMvc.perform(get("/api/payment/pay-1/status")).andExpect(status().isOk());
            // The caller's Asaas customer id must be propagated to the service for the ownership check.
            verify(paymentService).getPaymentStatus("pay-1", "cus_MINE");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void authenticatedCreatePaymentBindsCustomerToCallerNotRequestBody() throws Exception {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_MINE");
        final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(paymentService.createPayment(any(PaymentCreationRequest.class), eq("cus_MINE")))
                .thenReturn(new PaymentCreationResponse(
                        "pay-1",
                        LocalDateTime.now(),
                        "cus_MINE",
                        PaymentMethod.PIX,
                        PaymentStatus.PENDING,
                        LocalDateTime.now(),
                        "http://invoice",
                        "001"));

        try {
            mockMvc.perform(
                            post("/api/payment/create")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"paymentProcessor\":\"ASAAS\",\"customerId\":\"cus_OTHER\",\"value\":10.0,\"billingType\":\"PIX\"}"))
                    .andExpect(status().isOk());
            final ArgumentCaptor<PaymentCreationRequest> requestCaptor =
                    ArgumentCaptor.forClass(PaymentCreationRequest.class);
            // The service must receive the caller's id separately so it can ignore the
            // spoofable customerId in the body ("cus_OTHER").
            verify(paymentService).createPayment(requestCaptor.capture(), eq("cus_MINE"));
            assertEquals("cus_OTHER", requestCaptor.getValue().getCustomerId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
