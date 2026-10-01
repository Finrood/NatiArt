package com.portcelana.natiart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.portcelana.natiart.configuration.DatabaseTokenValidationCache;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.RateLimitWindowRepository;
import com.portcelana.natiart.repository.TokenValidationCacheRepository;
import com.portcelana.natiart.service.AsaasPaymentService;
import com.portcelana.natiart.service.DatabaseRateLimitStore;
import com.portcelana.natiart.service.ShippingService;

@ActiveProfiles("local-h2")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:ca3-context-boot;DB_CLOSE_DELAY=-1",
            "spring.sql.init.mode=never",
            "natiart.payment.asaas.apikey=ca3-smoke-only-key",
            "natiart.payment.asaas.payments-url=http://127.0.0.1:1/payments",
            "melhorenvio.api.url=http://127.0.0.1:1/shipping",
            "melhorenvio.api.token=ca3-smoke-only-token",
            "directory.service.url=http://127.0.0.1:1",
            "directory.service.auth-cache.ttl-millis=12345",
            "directory.service.auth-cache.max-entries=17"
        })
class ProductContextBootSmokeTest {

    @Test
    void fullApplicationStartsWithLocalH2AndManagedProductionConstructors(ApplicationContext context)
            throws SQLException {
        final DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("H2", connection.getMetaData().getDatabaseProductName());
        }

        final AsaasPaymentService paymentService = context.getBean(AsaasPaymentService.class);
        final ShippingService shippingService = context.getBean(ShippingService.class);
        final DatabaseTokenValidationCache tokenCache = context.getBean(DatabaseTokenValidationCache.class);
        final DatabaseRateLimitStore rateLimitStore = context.getBean(DatabaseRateLimitStore.class);

        assertNotNull(paymentService);
        assertNotNull(shippingService);
        assertNotNull(tokenCache);
        assertNotNull(rateLimitStore);
        assertEquals("ca3-smoke-only-key", ReflectionTestUtils.getField(paymentService, "asaasApiKey"));
        assertSame(
                context.getBean(PaymentRepository.class),
                ReflectionTestUtils.getField(paymentService, "paymentRepository"));
        assertEquals("http://127.0.0.1:1/shipping", ReflectionTestUtils.getField(shippingService, "apiUrl"));
        assertEquals("ca3-smoke-only-token", ReflectionTestUtils.getField(shippingService, "apiToken"));
        assertSame(
                context.getBean(TokenValidationCacheRepository.class),
                ReflectionTestUtils.getField(tokenCache, "repository"));
        assertEquals(12345L, ReflectionTestUtils.getField(tokenCache, "ttlMillis"));
        assertEquals(17, ReflectionTestUtils.getField(tokenCache, "maxEntries"));
        assertSame(
                context.getBean(RateLimitWindowRepository.class),
                ReflectionTestUtils.getField(rateLimitStore, "repository"));
    }
}
