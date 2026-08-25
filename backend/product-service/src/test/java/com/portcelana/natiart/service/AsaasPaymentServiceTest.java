package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
@ExtendWith(MockitoExtension.class)
class AsaasPaymentServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private AsaasPaymentService newService() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        return new AsaasPaymentService(builder);
    }

    @Test
    void ownershipCheckRejectsPaymentOwnedByAnotherCustomer() {
        assertThrows(UserNotAllowedException.class,
                () -> newService().requireOwnedPayment("cus_OTHER", "cus_MINE"));
    }

    @Test
    void ownershipCheckRejectsAnonymousOrUnknownRequester() {
        final AsaasPaymentService service = newService();
        assertThrows(UserNotAllowedException.class, () -> service.requireOwnedPayment("cus_OWNER", null));
        assertThrows(UserNotAllowedException.class, () -> service.requireOwnedPayment(null, "cus_SOMEONE"));
    }

    @Test
    void ownershipCheckAllowsTheOwner() {
        assertDoesNotThrow(() -> newService().requireOwnedPayment("cus_OWNER", "cus_OWNER"));
    }
}
