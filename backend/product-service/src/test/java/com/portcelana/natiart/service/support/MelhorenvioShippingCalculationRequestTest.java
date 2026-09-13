package com.portcelana.natiart.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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

    @Test
    void from_preservesEachAuthoritativeProductVolume() {
        final MelhorenvioShippingCalculationRequest request = MelhorenvioShippingCalculationRequest.from(
                List.of(
                        new ShippingEstimateRequest("88000000", 0.4f, 20.0f, 15.0f, 10.0f, 2),
                        new ShippingEstimateRequest("88000000", 0.8f, 60.0f, 45.0f, 30.0f, 1)),
                "01310923");

        assertEquals(2, request.getVolumes().size());
        assertEquals("0.4", request.getVolumes().get(0).getWeight());
        assertEquals("20.0", request.getVolumes().get(0).getLength());
        assertEquals(2, request.getVolumes().get(0).getQntd());
        assertEquals("60.0", request.getVolumes().get(1).getLength());
    }
}
