package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;

class PaymentIdempotencyRecoveryTest {
    @Test
    void staleInProgressReservationMovesToRecoverableState() {
        final PaymentIdempotencyRepository repository = mock(PaymentIdempotencyRepository.class);
        final PaymentIdempotency record = new PaymentIdempotency("cus-1", "key-1", "fingerprint");
        when(repository.findStaleByStatus(any(), any(Instant.class), any())).thenReturn(List.of(record));
        final PaymentIdempotencyService service = new PaymentIdempotencyService(repository, 1000);

        service.recoverStaleReservations();

        assertEquals(PaymentIdempotencyStatus.FAILED_RECOVERABLE, record.getStatus());
        verify(repository).save(record);
    }
}
