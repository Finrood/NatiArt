package com.portcelana.natiart.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.*;
import com.portcelana.natiart.repository.*;

@Service
public class GuestOrderClaimManager {
    private final GuestOrderClaimRepository claims;
    private final OrderRepository orders;

    public GuestOrderClaimManager(GuestOrderClaimRepository claims, OrderRepository orders) {
        this.claims = claims;
        this.orders = orders;
    }
    /** Repeated deliveries are safe and never replace an order's billing identity. */
    @Transactional
    public void link(String id, String accountId, String email, Instant cutoff) {
        if (id == null
                || !id.matches("[a-f0-9-]{36}")
                || accountId == null
                || !accountId.matches("[a-f0-9-]{36}")
                || email == null
                || email.length() > 255
                || cutoff == null
                || cutoff.isAfter(Instant.now().plusSeconds(30))) throw new IllegalArgumentException("Invalid claim");
        final String normalized = email.trim().toLowerCase(Locale.ROOT);
        final GuestOrderClaim existing = claims.findById(id).orElse(null);
        if (existing == null) claims.saveAndFlush(new GuestOrderClaim(id, accountId, normalized, cutoff));
        else if (!accountId.equals(existing.getAccountId())
                || !normalized.equals(existing.getEmail())
                || !cutoff.equals(existing.getCutoff())) throw new IllegalArgumentException("Conflicting claim");
        orders.claimGuestOrders(accountId, normalized, cutoff);
    }
    /** Also links a purchase that committed after the original claim callback. */
    @Scheduled(fixedDelayString = "${natiart.guest.claim-reconcile-delay-millis:30000}")
    @Transactional
    public void reconcile() {
        for (final String id : orders.findClaimableIds(PageRequest.of(0, 100))) {
            final CustomerOrder order = orders.findByIdForUpdate(id).orElse(null);
            if (order == null || order.getAccountOwnerId() != null) continue;
            claims.findFirstByEmailOrderByCutoffDesc(order.getEmail().trim().toLowerCase(Locale.ROOT))
                    .filter(claim -> !order.getOrderDate().isAfter(claim.getCutoff()))
                    .ifPresent(claim -> order.setAccountOwnerId(claim.getAccountId()));
        }
    }
}
