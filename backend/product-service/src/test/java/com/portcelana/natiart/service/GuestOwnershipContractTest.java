package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.*;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.*;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({
    GuestOrderClaimManager.class,
    GuestOrderManager.class,
    GuestTrackingManager.class,
    AccountOrderAccessManager.class
})
class GuestOwnershipContractTest {
    @Autowired
    private OrderRepository orders;

    @Autowired
    private GuestOrderClaimRepository claims;

    @Autowired
    private GuestOrderClaimManager linking;

    @Autowired
    private GuestOrderManager guests;

    @Autowired
    private GuestTrackingManager tracking;

    @Autowired
    private AccountOrderAccessManager accounts;

    @MockitoBean
    private OrderViewService views;

    private final String email = "guest@example.test";
    private final String guestId = UUID.randomUUID().toString();
    private final String accountId = UUID.randomUUID().toString();
    private final String billingId = "cus_guest_payer";
    private final Instant cutoff = Instant.parse("2026-09-01T00:00:00Z");

    private CustomerOrder order(String email, String guest, Instant placed) {
        return orders.saveAndFlush(new CustomerOrder()
                .setFirstname("Guest")
                .setLastname("Buyer")
                .setEmail(email)
                .setOwnerExternalId(billingId)
                .setGuestCustomerId(guest)
                .setOrderDate(placed)
                .setDeliveryAmount(BigDecimal.TEN)
                .setTotalAmount(BigDecimal.TEN)
                .setStatus(OrderStatus.PENDING));
    }

    private GuestCheckoutDto guest(String customerId) {
        return new GuestCheckoutDto(
                "session",
                customerId,
                email,
                null,
                "csrf",
                billingId,
                "SUCCEEDED",
                Instant.now().plusSeconds(60),
                false,
                null);
    }

    private AuthenticationResponseDto.Principal principal(String id, String external) throws Exception {
        return new ObjectMapper()
                .readValue(
                        "{\"id\":\"" + id + "\",\"externalId\":\"" + external + "\"}",
                        AuthenticationResponseDto.Principal.class);
    }

    @Test
    void guestCapabilityCannotReadAnotherCustomerOrderOrAClaimedOrder() {
        final CustomerOrder order = order(email, guestId, cutoff.minusSeconds(1));
        assertEquals(billingId, guests.orderOwnerOrDie(guest(guestId), order.getId()));
        assertThrows(
                UserNotAllowedException.class,
                () -> guests.orderOwnerOrDie(guest(UUID.randomUUID().toString()), order.getId()));
        order.setAccountOwnerId(accountId);
        orders.saveAndFlush(order);
        assertThrows(UserNotAllowedException.class, () -> guests.orderOwnerOrDie(guest(guestId), order.getId()));
    }

    @Test
    void claimingUsesOriginalEmailAndCutoffAndPreservesBillingIdentity() throws Exception {
        final CustomerOrder old = order(email, guestId, cutoff.minusSeconds(1));
        final CustomerOrder future = order(email, guestId, cutoff.plusSeconds(1));
        final CustomerOrder accountOrder = order(email, null, cutoff.minusSeconds(1));
        final CustomerOrder other = order("someone@example.test", guestId, cutoff.minusSeconds(1));
        final CustomerOrder alreadyClaimed = order(email, guestId, cutoff.minusSeconds(1));
        alreadyClaimed.setAccountOwnerId("other-account");
        orders.saveAndFlush(alreadyClaimed);
        final String claimId = UUID.randomUUID().toString();
        linking.link(claimId, accountId, email.toUpperCase(), cutoff);
        linking.link(claimId, accountId, email, cutoff);
        assertEquals(1, claims.count());
        assertEquals(accountId, orders.findById(old.getId()).orElseThrow().getAccountOwnerId());
        assertEquals(billingId, orders.findById(old.getId()).orElseThrow().getOwnerExternalId());
        for (final CustomerOrder excluded : java.util.List.of(future, accountOrder, other))
            assertNull(orders.findById(excluded.getId()).orElseThrow().getAccountOwnerId());
        assertEquals(
                "other-account",
                orders.findById(alreadyClaimed.getId()).orElseThrow().getAccountOwnerId());
        assertEquals(billingId, accounts.billingOwnerOrDie(principal(accountId, "cus_new_account"), old.getId()));
        assertThrows(
                ResourceNotFoundException.class,
                () -> accounts.billingOwnerOrDie(
                        principal(UUID.randomUUID().toString(), "cus_new_account"), old.getId()));
        assertThrows(
                IllegalArgumentException.class,
                () -> linking.link(claimId, UUID.randomUUID().toString(), email, cutoff));
    }

    @Test
    void aLateCommittingGuestOrderIsReconciledFromTheDurableClaim() {
        linking.link(UUID.randomUUID().toString(), accountId, email, cutoff);
        final CustomerOrder late = order(email, guestId, cutoff.minusSeconds(1));
        linking.reconcile();
        orders.flush();
        assertEquals(accountId, orders.findById(late.getId()).orElseThrow().getAccountOwnerId());
        assertEquals(billingId, orders.findById(late.getId()).orElseThrow().getOwnerExternalId());
    }

    @Test
    void mailboxScopeCannotExposeRegisteredOrdersOrOtherEmailsOrLaterPurchases() {
        final GuestCheckoutClient.TrackingScope scope = new GuestCheckoutClient.TrackingScope(
                email, cutoff, Instant.now().plusSeconds(60));
        final CustomerOrder permitted = order(email, guestId, cutoff.minusSeconds(1));
        assertEquals(billingId, tracking.ownerOrDie(scope, permitted.getId()));
        for (final CustomerOrder excluded : java.util.List.of(
                order("other@example.test", guestId, cutoff),
                order(email, null, cutoff),
                order(email, guestId, cutoff.plusSeconds(1))))
            assertThrows(ResourceNotFoundException.class, () -> tracking.ownerOrDie(scope, excluded.getId()));
        when(views.getCustomerOrder(eq(permitted.getId()), eq(billingId))).thenReturn(OrderDto.from(permitted));
        assertEquals(1, tracking.history(scope, 0).size());
    }
}
