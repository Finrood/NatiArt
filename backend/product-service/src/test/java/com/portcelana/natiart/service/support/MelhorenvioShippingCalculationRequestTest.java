package com.portcelana.natiart.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;

class MelhorenvioShippingCalculationRequestTest {

    @Test
    void from_usesConfiguredOriginPostalCode() {
        final ShippingEstimateRequest estimate = new ShippingEstimateRequest("88000000", 1.0f, 10.0f, 10.0f, 10.0f, 1);

        final MelhorenvioShippingCalculationRequest request =
                MelhorenvioShippingCalculationRequest.from(estimate, "01310923");

        assertEquals("01310923", request.getFrom().getPostal_code());
        assertEquals("88000000", request.getTo().getPostal_code());
    }
}
