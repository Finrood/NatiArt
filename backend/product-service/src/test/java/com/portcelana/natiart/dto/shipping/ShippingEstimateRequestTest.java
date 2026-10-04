package com.portcelana.natiart.dto.shipping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.portcelana.natiart.service.support.InputValidationException;

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

    @Test
    void constructor_normalizesFormattedCepButRejectsInjectedCharacters() {
        assertEquals("88010000", new ShippingEstimateRequest("88010-000", 1, 10, 10, 10, 1).getTo());
        final InputValidationException failure = assertThrows(
                InputValidationException.class, () -> new ShippingEstimateRequest("abc88010000", 1, 10, 10, 10, 1));
        assertEquals("zipCode", failure.getField());
    }

    @Test
    void constructor_rejectsNonFiniteOversizedAndExcessiveQuantity() {
        assertEquals(
                "weight",
                assertThrows(
                                InputValidationException.class,
                                () -> new ShippingEstimateRequest("88010000", Float.POSITIVE_INFINITY, 10, 10, 10, 1))
                        .getField());
        assertEquals(
                "length",
                assertThrows(
                                InputValidationException.class,
                                () -> new ShippingEstimateRequest("88010000", 1, 201, 10, 10, 1))
                        .getField());
        assertEquals(
                "quantity",
                assertThrows(
                                InputValidationException.class,
                                () -> new ShippingEstimateRequest("88010000", 1, 10, 10, 10, 101))
                        .getField());
        assertDoesNotThrow(() -> new ShippingEstimateRequest("88010000", 100, 200, 200, 200, 100));
    }
}
