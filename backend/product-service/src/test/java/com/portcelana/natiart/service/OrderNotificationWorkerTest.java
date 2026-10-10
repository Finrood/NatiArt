package com.portcelana.natiart.service;

import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderNotificationWorkerTest {
    @Mock
    private OrderNotificationManager jobs;

    @Mock
    private OrderNotificationSender sender;

    @InjectMocks
    private OrderNotificationWorker worker;

    @Test
    void failureIsRecordedAndDoesNotPreventNextDelivery() {
        final OrderMailMessage first = new OrderMailMessage("first", "lease1", "one@example.test", "subject", "body");
        final OrderMailMessage second = new OrderMailMessage("second", "lease2", "two@example.test", "subject", "body");
        when(jobs.due()).thenReturn(List.of("first", "second"));
        when(jobs.claim("first")).thenReturn(first);
        when(jobs.claim("second")).thenReturn(second);
        doThrow(new IllegalStateException("private provider response"))
                .when(sender)
                .send(first);
        worker.deliver();
        verify(jobs).complete(first, false);
        verify(jobs).complete(second, true);
    }
}
