package com.portcelana.natiart.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final long staleReservationMillis;

    @Autowired
    public PaymentIdempotencyService(
            PaymentIdempotencyRepository repository,
            @Value("${natiart.payment.idempotency.stale-reservation-millis:900000}") long staleReservationMillis) {
        this.repository = repository;
        if (staleReservationMillis <= 0) {
            throw new IllegalArgumentException("The payment reservation stale interval must be positive");
        }
        this.staleReservationMillis = staleReservationMillis;
    }

    /** Test-friendly constructor with the production default interval. */
    public PaymentIdempotencyService(PaymentIdempotencyRepository repository) {
        this(repository, 900000);
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

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentIdempotency> find(String ownerExternalId, String idempotencyKey) {
        return repository.findByOwnerExternalIdAndIdempotencyKey(ownerExternalId, idempotencyKey);
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
                .findStaleByStatus(
                        PaymentIdempotencyStatus.IN_PROGRESS, cutoff, PageRequest.of(0, 100))
                .forEach(record -> {
                    record.setStatus(PaymentIdempotencyStatus.FAILED_RECOVERABLE);
                    repository.save(record);
                });
    }
}
