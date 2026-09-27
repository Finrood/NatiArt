package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.repository.RoleRepository;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaAuditConfigTest {
    @MockitoBean(name = "utcClock")
    private Clock utcClock;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistedRoleKeepsCreationTimeAndAdvancesModificationTime() {
        final Instant created = Instant.parse("2025-01-02T03:04:05Z");
        final Instant modified = Instant.parse("2025-01-03T04:05:06Z");
        final AtomicReference<Instant> now = new AtomicReference<>(created);
        when(utcClock.instant()).thenAnswer(invocation -> now.get());

        final Role role = roleRepository.saveAndFlush(new Role(RoleName.USER));
        final String id = role.getId();
        entityManager.clear();

        final Role persisted = roleRepository.findById(id).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(persisted, "createdAt"));
        assertEquals(created, ReflectionTestUtils.getField(persisted, "updatedAt"));

        now.set(modified);
        persisted.setDescription("Updated role");
        entityManager.flush();
        entityManager.clear();

        final Role updated = roleRepository.findById(id).orElseThrow();
        assertEquals(created, ReflectionTestUtils.getField(updated, "createdAt"));
        assertEquals(modified, ReflectionTestUtils.getField(updated, "updatedAt"));
    }
}
