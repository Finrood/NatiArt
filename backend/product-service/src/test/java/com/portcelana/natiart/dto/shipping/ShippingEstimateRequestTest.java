package com.portcelana.natiart.dto.shipping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ShippingEstimateRequestTest {

    @Test
    void constructor_acceptsValidEstimate() {
        assertDoesNotThrow(() -> new ShippingEstimateRequest("88010000", 1.5f, 20.0f, 15.0f, 10.0f, 2));
    }

    @Test
    void constructor_rejectsMissingDestination() {
        assertThrows(
                IllegalArgumentException.class, () -> new ShippingEstimateRequest(null, 1.0f, 1.0f, 1.0f, 1.0f, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ShippingEstimateRequest("  ", 1.0f, 1.0f, 1.0f, 1.0f, 1));
    }

    @Test
    void constructor_rejectsNonPositiveWeightOrDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShippingEstimateRequest("88010000", 0.0f, 1.0f, 1.0f, 1.0f, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShippingEstimateRequest("88010000", 1.0f, -1.0f, 1.0f, 1.0f, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShippingEstimateRequest("88010000", 1.0f, 1.0f, 0.0f, 1.0f, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShippingEstimateRequest("88010000", 1.0f, 1.0f, 1.0f, -2.0f, 1));
    }

    @Test
    void constructor_rejectsQuantityBelowOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShippingEstimateRequest("88010000", 1.0f, 1.0f, 1.0f, 1.0f, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShippingEstimateRequest("88010000", 1.0f, 1.0f, 1.0f, 1.0f, -3));
    }

    @Test
    void constructor_keepsValidValues() {
        final ShippingEstimateRequest request = new ShippingEstimateRequest("88010000", 1.5f, 20.0f, 15.0f, 10.0f, 2);
        assertEquals("88010000", request.getTo());
        assertEquals(2, request.getQuantity());
    }
}
