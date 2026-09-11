package com.portcelana.natiart.service;

import com.portcelana.natiart.model.PaymentIdempotency;

/** Result of the durable reservation attempt; only {@code acquired} may call the provider. */
public record PaymentIdempotencyReservation(PaymentIdempotency record, boolean acquired) {}
