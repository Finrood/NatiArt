package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;

class ShippingProviderContractTest {
    private static final String URL = "https://carrier.example.test/calculate";
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer provider =
            MockRestServiceServer.bindTo(client).build();
    private final ShippingService shipping =
            new ShippingService(URL, "fixture-token", "88058-380", "NatiArt (shipping@example.test)", client);

    @Test
    void configuredPriceAndTimeAreUsedWithoutAnInventedSurcharge() {
        respond("""
                [{"id":1,"name":"PAC","price":"18.50","custom_price":"21.90",
                  "delivery_time":5,"custom_delivery_time":8,"currency":"BRL","company":{"name":"Correios"}}]
                """);
        final ShippingEstimate estimate = estimates().getFirst();
        assertEquals(new BigDecimal("21.90"), estimate.getPrice());
        assertEquals(8, estimate.getEstimatedDeliveryDays());
        provider.verify();
    }

    @Test
    void absentCustomFieldsFallBackAndOptionsAreSortedByTheEffectivePrice() {
        respond("""
                [{"id":1,"name":"PAC","price":"15.00","custom_price":"30.00","delivery_time":4,"company":{"name":"Correios"}},
                 {"id":2,"name":"SEDEX","price":"20.00","delivery_time":2,"company":{"name":"Correios"}}]
                """);
        final List<ShippingEstimate> estimates = estimates();
        assertEquals("2", estimates.getFirst().getServiceId());
        assertEquals(new BigDecimal("20.00"), estimates.getFirst().getPrice());
        assertEquals(2, estimates.getFirst().getEstimatedDeliveryDays());
        provider.verify();
    }

    @Test
    void explicitFreeCustomPriceAndZeroDaysArePreserved() {
        respond("""
                [{"id":1,"name":"PAC","price":"10.00","custom_price":"0.00",
                  "delivery_time":3,"custom_delivery_time":0,"company":{"name":"Correios"}}]
                """);
        assertEquals(new BigDecimal("0.00"), estimates().getFirst().getPrice());
        provider.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"custom_price\":\"-1.00\",\"delivery_time\":3",
                "\"custom_price\":\"10.001\",\"delivery_time\":3",
                "\"custom_price\":\"100000000.00\",\"delivery_time\":3",
                "\"currency\":\"USD\",\"delivery_time\":3",
                "\"custom_delivery_time\":-1,\"delivery_time\":3",
                "\"error\":\"unavailable\",\"delivery_time\":3",
                "\"delivery_time\":null"
            })
    void unusableOptionsAreNeverCharged(String fields) {
        respond("[{\"id\":1,\"name\":\"PAC\",\"price\":10,\"company\":{\"name\":\"Correios\"}," + fields + "}]");
        assertEquals(List.of(), estimates());
        provider.verify();
    }

    @Test
    void malformedProviderMoneyReturnsAnUpstreamError() {
        respond("[{\"id\":1,\"price\":\"invalid\"}]");
        final UpstreamServiceException exception = assertThrows(UpstreamServiceException.class, this::estimates);
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getHttpStatus());
        provider.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "NatiArt", "NatiArt (contact)", "NatiArt (shipping@example.test)\r\nInjected: value"})
    void technicalContactIsRequiredAndCannotInjectHeaders(String userAgent) {
        assertThrows(
                IllegalStateException.class, () -> new ShippingService(URL, "fixture-token", "88058380", userAgent));
    }

    @Test
    void onlyExplicitlyEnabledCarriersAreOffered() {
        shipping.setAllowedCompanies(List.of("Correios", "Jadlog"));
        respond("""
                [{"id":1,"name":"Package","price":"8.00","delivery_time":4,"company":{"name":"JadLog"}},
                 {"id":2,"name":"Express","price":"7.00","delivery_time":2,"company":{"name":"Unapproved"}}]
                """);
        final List<ShippingEstimate> estimates = estimates();
        assertEquals(1, estimates.size());
        assertEquals("1", estimates.getFirst().getServiceId());
        provider.verify();
    }

    private List<ShippingEstimate> estimates() {
        return shipping.getShippingEstimates(new ShippingEstimateRequest("01001000", 0.5f, 20, 15, 10, 1));
    }

    private void respond(String json) {
        provider.expect(requestTo(URL))
                .andExpect(header("User-Agent", "NatiArt (shipping@example.test)"))
                .andExpect(header("Authorization", "Bearer fixture-token"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
