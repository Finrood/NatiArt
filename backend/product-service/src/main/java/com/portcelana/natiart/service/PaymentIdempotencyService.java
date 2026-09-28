package com.portcelana.natiart.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;

/**
 * Performs reservation and state transitions in independent transactions.
 * In particular, a unique-key loser must leave its failed insert transaction
 * before it reads the winning reservation.
 */
@Service
public class PaymentIdempotencyService {
    private final PaymentIdempotencyRepository repository;
    private final OrderRepository orderRepository;
    private final long staleReservationMillis;

    @Autowired
    public PaymentIdempotencyService(
            PaymentIdempotencyRepository repository,
            OrderRepository orderRepository,
            @Value("${natiart.payment.idempotency.stale-reservation-millis:900000}") long staleReservationMillis) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        if (staleReservationMillis <= 0) {
            throw new IllegalArgumentException("The payment reservation stale interval must be positive");
        }
        this.staleReservationMillis = staleReservationMillis;
    }

    /** Test-friendly constructor with the production default interval. */
    public PaymentIdempotencyService(PaymentIdempotencyRepository repository) {
        this(repository, null, 900000);
    }

    PaymentIdempotencyService(PaymentIdempotencyRepository repository, long staleReservationMillis) {
        this(repository, null, staleReservationMillis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIdempotencyReservation reserve(
            String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        return reserve(ownerExternalId, idempotencyKey, requestFingerprint, null);
    }

    /** Locks the order before recording a payment attempt so expiry cannot race provider egress. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIdempotencyReservation reserve(
            String ownerExternalId, String idempotencyKey, String requestFingerprint, String orderId) {
        if (orderId != null) {
            lockPendingOrder(ownerExternalId, orderId);
        }
        final Optional<PaymentIdempotency> existing =
                repository.findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey);
        if (existing.isPresent()) {
            final boolean legacyCompletedReplay = existing.get().getOrderId() == null
                    && existing.get().getStatus() == PaymentIdempotencyStatus.SUCCEEDED;
            if (!java.util.Objects.equals(existing.get().getOrderId(), orderId) && !legacyCompletedReplay) {
                throw new IllegalArgumentException("Idempotency-Key was already used for another order");
            }
            return new PaymentIdempotencyReservation(existing.get(), false);
        }
        // saveAndFlush makes the unique insert the serialization point before
        // any provider network call is possible.
        try {
            final PaymentIdempotency record =
                    new PaymentIdempotency(ownerExternalId, idempotencyKey, requestFingerprint, orderId);
            final PaymentIdempotency saved = repository.saveAndFlush(record);
            return new PaymentIdempotencyReservation(saved != null ? saved : record, true);
        } catch (DataIntegrityViolationException e) {
            // The caller deliberately handles this outside this transaction;
            // rethrowing ensures Spring rolls back the losing insert.
            throw e;
        }
    }

    /**
     * Reserves the single payment attempt allowed for an order. The client key
     * remains useful for replay diagnostics, but it is not the serialization
     * key: callers using different keys for the same order receive this same
     * server-owned reservation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIdempotencyReservation reserveForOrder(
            String ownerExternalId, String orderId, String idempotencyKey, String requestFingerprint) {
        lockPendingOrder(ownerExternalId, orderId);
        final Optional<PaymentIdempotency> existingOrder =
                repository.findByOwnerExternalIdAndOrderId(ownerExternalId, orderId);
        if (existingOrder.isPresent()) {
            return new PaymentIdempotencyReservation(existingOrder.get(), false);
        }
        final Optional<PaymentIdempotency> existingKey =
                repository.findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey);
        if (existingKey.isPresent()) {
            return new PaymentIdempotencyReservation(existingKey.get(), false);
        }
        try {
            final PaymentIdempotency record =
                    new PaymentIdempotency(ownerExternalId, idempotencyKey, requestFingerprint, orderId);
            final PaymentIdempotency saved = repository.saveAndFlush(record);
            return new PaymentIdempotencyReservation(saved != null ? saved : record, true);
        } catch (DataIntegrityViolationException e) {
            // The caller leaves this failed transaction before reloading the
            // winning order reservation.
            throw e;
        }
    }

    private void lockPendingOrder(String ownerExternalId, String orderId) {
        final CustomerOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order is unavailable for payment"));
        if (!ownerExternalId.equals(order.getOwnerExternalId()) || order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Order is unavailable for payment");
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentIdempotency> find(String ownerExternalId, String idempotencyKey) {
        return repository.findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentIdempotency> findForOrder(String ownerExternalId, String orderId) {
        return repository.findByOwnerExternalIdAndOrderId(ownerExternalId, orderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(String ownerExternalId, String idempotencyKey, String providerPaymentId) {
        repository
                .findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey)
                .ifPresent(record -> {
                    record.setProviderPaymentId(providerPaymentId).setStatus(PaymentIdempotencyStatus.SUCCEEDED);
                    repository.save(record);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRecoverableFailure(String ownerExternalId, String idempotencyKey) {
        repository
                .findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey)
                .ifPresent(record -> {
                    if (record.getStatus() == PaymentIdempotencyStatus.IN_PROGRESS) {
                        record.setStatus(PaymentIdempotencyStatus.FAILED_RECOVERABLE);
                        repository.save(record);
                    }
                });
    }

    /** Moves abandoned reservations into the explicit reconciliation state after a restart. */
    @Scheduled(fixedDelayString = "${natiart.payment.idempotency.recovery-delay-millis:60000}")
    @Transactional
    public void recoverStaleReservations() {
        final Instant cutoff = Instant.now().minusMillis(staleReservationMillis);
        repository
                .findStaleByStatus(PaymentIdempotencyStatus.IN_PROGRESS, cutoff, PageRequest.of(0, 100))
                .forEach(record -> {
                    record.setStatus(PaymentIdempotencyStatus.FAILED_RECOVERABLE);
                    repository.save(record);
                });
    }
}
