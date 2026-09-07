package com.portcelana.natiart.dto.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
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
        return new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_1", new BigDecimal("10.00"), PaymentMethod.PIX, clock);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-04T20:59:00Z"), ZONE);
    }

    @Test
    void constructor_acceptsValidRequest() {
        final PaymentCreationRequest request = requestAt("2026-09-04T20:59:00Z");
        assertEquals(PaymentProcessor.ASAAS, request.getPaymentProcessor());
        assertEquals("cus_1", request.getCustomerId());
        assertEquals(new BigDecimal("10.00"), request.getValue());
        assertEquals(PaymentMethod.PIX, request.getBillingType());
    }

    @Test
    void constructor_preservesCentValueExactly() {
        final PaymentCreationRequest request = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_1", new BigDecimal("19.99"), PaymentMethod.PIX, fixedClock());
        assertEquals(new BigDecimal("19.99"), request.getValue());
        assertEquals(2, request.getValue().scale());
    }

    @Test
    void constructor_rejectsNullPaymentProcessor() {
        final Clock clock = fixedClock();
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCreationRequest(null, "cus_1", new BigDecimal("10.00"), PaymentMethod.PIX, clock));
    }

    @Test
    void constructor_rejectsNullBillingType() {
        final Clock clock = fixedClock();
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCreationRequest(
                        PaymentProcessor.ASAAS, "cus_1", new BigDecimal("10.00"), null, clock));
    }

    @Test
    void constructor_rejectsNullNonPositiveOrOverPreciseValue() {
        final Clock clock = fixedClock();
        final BigDecimal[] badValues = {
            null, BigDecimal.ZERO, new BigDecimal("-5.00"), new BigDecimal("10.001"), new BigDecimal("0.001")
        };
        for (final BigDecimal badValue : badValues) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PaymentCreationRequest(
                            PaymentProcessor.ASAAS, "cus_1", badValue, PaymentMethod.PIX, clock),
                    "value " + badValue + " must be rejected");
        }
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
