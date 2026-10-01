package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.service.AsaasPaymentService;
import com.portcelana.natiart.service.DatabaseRateLimitStore;
import com.portcelana.natiart.service.ShippingService;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:product-audit;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "natiart.payment.asaas.apikey=test-only-api-key"
        })
class JpaAuditConfigTest {
    @Autowired
    private MutableClock utcClock;

    @MockitoBean
    private DatabaseTokenValidationCache tokenValidationCache;

    @MockitoBean
    private DatabaseRateLimitStore rateLimitStore;

    @MockitoBean
    private ShippingService shippingService;

    @MockitoBean
    private AsaasPaymentService paymentService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistedCategoryKeepsCreationTimeAndAdvancesModificationTime() {
        final Instant created = Instant.parse("2025-01-02T03:04:05Z");
        final Instant modified = Instant.parse("2025-01-03T04:05:06Z");
        utcClock.setInstant(created);

        final Category category = categoryRepository.saveAndFlush(new Category("audit-category"));
        final String id = category.getId();
        entityManager.clear();

        final Category persisted = categoryRepository.findById(id).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(persisted, "createdAt"));
        assertEquals(created, ReflectionTestUtils.getField(persisted, "updatedAt"));

        utcClock.setInstant(modified);
        persisted.setDescription("Updated category");
        entityManager.flush();
        entityManager.clear();

        final Category updated = categoryRepository.findById(id).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(updated, "createdAt"));
        assertEquals(modified, ReflectionTestUtils.getField(updated, "updatedAt"));
    }

    @TestConfiguration
    static class ClientTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);

        void setInstant(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now.get(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
