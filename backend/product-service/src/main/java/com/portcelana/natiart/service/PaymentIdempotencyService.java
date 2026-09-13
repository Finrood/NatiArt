package com.portcelana.natiart.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;

/**
 * Performs reservation and state transitions in independent transactions.
 * In particular, a unique-key loser must leave its failed insert transaction
 * before it reads the winning reservation.
 */
@Service
public class PaymentIdempotencyService {
    private final PaymentIdempotencyRepository repository;

    public PaymentIdempotencyService(PaymentIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIdempotencyReservation reserve(
            String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        final Optional<PaymentIdempotency> existing =
                repository.findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey);
        if (existing.isPresent()) {
            return new PaymentIdempotencyReservation(existing.get(), false);
        }
        // saveAndFlush makes the unique insert the serialization point before
        // any provider network call is possible.
        try {
            final PaymentIdempotency record =
                    new PaymentIdempotency(ownerExternalId, idempotencyKey, requestFingerprint);
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
                    new PaymentIdempotency(ownerExternalId, idempotencyKey, orderId, requestFingerprint);
            final PaymentIdempotency saved = repository.saveAndFlush(record);
            return new PaymentIdempotencyReservation(saved != null ? saved : record, true);
        } catch (DataIntegrityViolationException e) {
            // The caller leaves this failed transaction before reloading the
            // winning order reservation.
            throw e;
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
}
