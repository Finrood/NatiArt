package com.saas.directory.configuration;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.repository.RoleRepository;

@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:directory-audit;DB_CLOSE_DELAY=-1"
        })
class JpaAuditConfigTest {
    @Autowired
    private MutableClock utcClock;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistedRoleKeepsCreationTimeAndAdvancesModificationTime() {
        final Instant created = Instant.parse("2025-01-02T03:04:05Z");
        final Instant modified = Instant.parse("2025-01-03T04:05:06Z");
        final Role role = roleRepository.findRoleByLabel(RoleName.USER).orElseThrow();
        final Role admin = roleRepository.findRoleByLabel(RoleName.ADMIN).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(admin, "createdAt"));
        final String id = role.getId();
        entityManager.clear();

        final Role persisted = roleRepository.findById(id).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(persisted, "createdAt"));
        assertEquals(created, ReflectionTestUtils.getField(persisted, "updatedAt"));

        utcClock.setInstant(modified);
        persisted.setDescription("Updated role");
        entityManager.flush();
        entityManager.clear();

        final Role updated = roleRepository.findById(id).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(updated, "createdAt"));
        assertEquals(modified, ReflectionTestUtils.getField(updated, "updatedAt"));
    }

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2025-01-02T03:04:05Z"));

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
