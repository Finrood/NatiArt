package com.portcelana.natiart.dto.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;

class PaymentCreationRequestTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    private PaymentCreationRequest requestAt(String instant) {
        final Clock clock = Clock.fixed(Instant.parse(instant), ZONE);
        return new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_1", 10.0, PaymentMethod.PIX, clock);
    }

    @Test
    void dueDate_isTomorrowBeforeCutoff() {
        assertEquals(LocalDate.of(2026, 9, 5), requestAt("2026-09-04T20:59:00Z").getDueDate());
    }

    @Test
    void dueDate_isTodayAtCutoff() {
        assertEquals(LocalDate.of(2026, 9, 4), requestAt("2026-09-04T21:00:00Z").getDueDate());
    }
}
