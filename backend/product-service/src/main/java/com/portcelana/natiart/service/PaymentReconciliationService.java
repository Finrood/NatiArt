package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest.AsaasWebhookPayment;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.PaymentWebhookEvent;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.PaymentWebhookEventRepository;

/** Applies authenticated provider state without depending on a buyer polling the UI. */
@Service
public class PaymentReconciliationService {
    public static final String WEBHOOK_TOKEN_HEADER = "asaas-access-token";

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final Set<String> KNOWN_PROVIDER_STATUSES = Set.of(
            "PENDING",
            "RECEIVED",
            "CONFIRMED",
            "OVERDUE",
            "REFUNDED",
            "REFUND_REQUESTED",
            "CHARGEBACK",
            "CANCELLED",
            "AWAITING_RISK_ANALYSIS",
            "DUNNING_REQUESTED",
            "AWAITING_CHARGEBACK_REVERSAL");

    private final String webhookToken;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final OrderManager orderManager;
    private final AsaasPaymentService asaasPaymentService;
    private final TransactionTemplate reconciliationTransaction;

    public PaymentReconciliationService(
            @Value("${natiart.payment.asaas.webhook-token:}") String webhookToken,
            PaymentRepository paymentRepository,
            PaymentWebhookEventRepository webhookEventRepository,
            OrderRepository orderRepository,
            OrderManager orderManager,
            AsaasPaymentService asaasPaymentService,
            PlatformTransactionManager transactionManager) {
        this.webhookToken = webhookToken;
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.orderRepository = orderRepository;
        this.orderManager = orderManager;
        this.asaasPaymentService = asaasPaymentService;
        this.reconciliationTransaction = new TransactionTemplate(transactionManager);
    }

    public boolean hasValidWebhookToken(String suppliedToken) {
        if (webhookToken == null || webhookToken.isBlank() || suppliedToken == null || suppliedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void processWebhook(AsaasWebhookRequest request) {
        requireWebhookShape(request);
        if (webhookEventRepository.findByProviderEventId(request.getId()).isPresent()) {
            return;
        }
        final AsaasWebhookPayment providerPayment = request.getPayment();
        final Payment localPayment = lockPayment(providerPayment.getId());
        validateSnapshot(
                localPayment,
                providerPayment.getId(),
                providerPayment.getCustomer(),
                providerPayment.getValue(),
                providerPayment.getCurrency(),
                providerPayment.getStatus());

        applyProviderState(localPayment, providerPayment.getStatus());
        webhookEventRepository.saveAndFlush(
                new PaymentWebhookEvent(request.getId(), request.getEvent(), providerPayment.getId()));
    }

    /** Polling is recovery only; the browser status endpoint is no longer the sole paid-order transition. */
    @Scheduled(fixedDelayString = "${natiart.payment.reconciliation.fixed-delay-millis:300000}")
    public void reconcilePendingPayments() {
        final Instant now = Instant.now();
        paymentRepository
                .findForReconciliation(
                        List.of(
                                "PENDING",
                                "AWAITING_RISK_ANALYSIS",
                                "OVERDUE",
                                "DUNNING_REQUESTED",
                                "AWAITING_CHARGEBACK_REVERSAL"),
                        now,
                        Instant.EPOCH,
                        PageRequest.of(0, 50))
                .forEach(paymentId -> reconcileOne(paymentId, now));
    }

    private void reconcileOne(String paymentId, Instant now) {
        try {
            // Commit the retry lease before network egress, so failed older records yield to later work.
            if (paymentRepository.scheduleReconciliation(paymentId, now, now.plusSeconds(60)) == 0) return;
            final AsaasPaymentCreationResponse providerPayment =
                    asaasPaymentService.fetchPaymentForReconciliation(paymentId);
            // The scheduler is a self-call; use an explicit transaction for payment + order atomicity.
            reconciliationTransaction.executeWithoutResult(
                    status -> reconcileProviderSnapshot(paymentId, providerPayment));
        } catch (RuntimeException e) {
            LOGGER.warn("Payment reconciliation deferred for provider payment [{}]: {}", paymentId, e.getMessage());
        }
    }

    private Payment lockPayment(String paymentId) {
        // Read only the scalar ID before locking: loading Payment here could cache stale provider state.
        final String orderId = paymentRepository
                .findOrderIdById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found during reconciliation"));
        orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found during reconciliation"));
        return paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found during reconciliation"));
    }

    @Transactional
    public void reconcileProviderSnapshot(String paymentId, AsaasPaymentCreationResponse providerPayment) {
        if (providerPayment == null) {
            throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
        }
        final Payment localPayment = lockPayment(paymentId);
        validateSnapshot(
                localPayment,
                providerPayment.getId(),
                providerPayment.getCustomer(),
                providerPayment.getValue() == null ? null : BigDecimal.valueOf(providerPayment.getValue()),
                null,
                providerPayment.getStatus());
        applyProviderState(localPayment, providerPayment.getStatus());
    }

    private void requireWebhookShape(AsaasWebhookRequest request) {
        if (request == null
                || isBlank(request.getId())
                || isBlank(request.getEvent())
                || request.getPayment() == null
                || isBlank(request.getPayment().getId())) {
            throw new IllegalArgumentException("Malformed payment webhook");
        }
    }

    private void validateSnapshot(
            Payment localPayment,
            String providerPaymentId,
            String providerCustomer,
            BigDecimal providerValue,
            String currency,
            String providerStatus) {
        if (!localPayment.getId().equals(providerPaymentId)
                || !localPayment.getOwnerExternalId().equals(providerCustomer)) {
            throw new IllegalArgumentException("Payment provider identity does not match the local ledger");
        }
        if (providerValue == null || providerValue.signum() <= 0 || !hasOrder(localPayment.getOrderId())) {
            throw new IllegalArgumentException("Payment provider amount or order is invalid");
        }
        final CustomerOrder order = orderRepository
                .findById(localPayment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found during reconciliation"));
        if (!localPayment.getOwnerExternalId().equals(order.getOwnerExternalId())
                || order.getTotalAmount() == null
                || order.getTotalAmount().compareTo(providerValue) != 0) {
            throw new IllegalArgumentException("Payment provider amount does not match the local order");
        }
        if (currency != null && !"BRL".equalsIgnoreCase(currency.trim())) {
            throw new IllegalArgumentException("Payment provider currency does not match BRL");
        }
        if (providerStatus == null
                || !KNOWN_PROVIDER_STATUSES.contains(providerStatus.trim().toUpperCase(Locale.ROOT))) {
            throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
        }
    }

    private void applyProviderState(Payment localPayment, String providerStatus) {
        final String normalizedStatus = providerStatus.trim().toUpperCase(Locale.ROOT);
        final boolean accepted = statusRank(normalizedStatus) >= statusRank(localPayment.getProviderStatus());
        if (accepted) {
            localPayment.setProviderStatus(normalizedStatus).setProviderUpdatedAt(Instant.now());
            paymentRepository.save(localPayment);
        }
        if (accepted
                && ("RECEIVED".equals(normalizedStatus) || "CONFIRMED".equals(normalizedStatus))
                && localPayment.getOrderId() != null) {
            final CustomerOrder order =
                    orderRepository.findById(localPayment.getOrderId()).orElse(null);
            // A delayed paid event must never revive cancelled fulfillment.
            if (order != null && order.getStatus() == OrderStatus.PENDING) {
                orderManager.markOrderPaid(localPayment.getOrderId());
            }
        }
    }

    private int statusRank(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case "RECEIVED", "CONFIRMED" -> 3;
            case "REFUNDED", "REFUND_REQUESTED", "CHARGEBACK", "CANCELLED" -> 4;
            default -> 1;
        };
    }

    private boolean hasOrder(String orderId) {
        return orderId != null && !orderId.isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
