package com.portcelana.natiart;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.portcelana.natiart.configuration.DatabaseTokenValidationCache;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.RateLimitWindowRepository;
import com.portcelana.natiart.service.AsaasPaymentService;
import com.portcelana.natiart.service.DatabaseRateLimitStore;
import com.portcelana.natiart.service.OrderManager;
import com.portcelana.natiart.service.PaymentIdempotencyService;
import com.portcelana.natiart.service.ShippingService;

class ServiceConstructorWiringTest {

    @Test
    void productionConstructorsAreSelectableByARealSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "natiart.payment.asaas.apikey=test-key",
                    "natiart.payment.asaas.payments-url=https://payments.example.test",
                    "melhorenvio.api.url=https://shipping.example.test",
                    "melhorenvio.api.token=test-token",
                    "melhorenvio.api.from-postal-code=88085201",
                    "directory.service.auth-cache.ttl-millis=30000",
                    "directory.service.auth-cache.max-entries=10");
            context.registerBean(PaymentRepository.class, () -> mock(PaymentRepository.class));
            context.registerBean(OrderRepository.class, () -> mock(OrderRepository.class));
            context.registerBean(PaymentIdempotencyRepository.class, () -> mock(PaymentIdempotencyRepository.class));
            context.registerBean(RateLimitWindowRepository.class, () -> mock(RateLimitWindowRepository.class));
            context.registerBean(ObjectMapper.class, ObjectMapper::new);
            context.registerBean(PaymentIdempotencyService.class, () -> mock(PaymentIdempotencyService.class));
            context.registerBean(OrderManager.class, () -> mock(OrderManager.class));
            context.registerBean(AsaasPaymentService.class);
            context.registerBean(ShippingService.class);
            context.registerBean(DatabaseTokenValidationCache.class);
            context.registerBean(DatabaseRateLimitStore.class);

            context.refresh();

            assertNotNull(context.getBean(AsaasPaymentService.class));
            assertNotNull(context.getBean(ShippingService.class));
            assertNotNull(context.getBean(DatabaseTokenValidationCache.class));
            assertNotNull(context.getBean(DatabaseRateLimitStore.class));
        }
    }
}
