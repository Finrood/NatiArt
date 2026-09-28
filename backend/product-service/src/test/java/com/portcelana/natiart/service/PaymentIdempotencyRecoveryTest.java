package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;

class PaymentIdempotencyRecoveryTest {
    @Test
    void completedLegacyAttemptCanReplayWhileOrderIsPending() {
        final PaymentIdempotencyRepository repository = mock(PaymentIdempotencyRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        final PaymentIdempotency record = new PaymentIdempotency("cus-1", "key-1", "fingerprint")
                .setProviderPaymentId("pay-1")
                .setStatus(PaymentIdempotencyStatus.SUCCEEDED);
        when(orderRepository.findByIdForUpdate("order-1"))
                .thenReturn(Optional.of(
                        new CustomerOrder().setOwnerExternalId("cus-1").setStatus(OrderStatus.PENDING)));
        when(repository.findByOwnerExternalIdAndIdempotencyKey("cus-1", "key-1"))
                .thenReturn(Optional.of(record));
        final PaymentIdempotencyService service = new PaymentIdempotencyService(repository, orderRepository, 1000);

        assertFalse(service.reserve("cus-1", "key-1", "fingerprint", "order-1").acquired());
    }

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
