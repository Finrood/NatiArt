package com.saas.directory.configuration;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.repository.RoleRepository;

/** Creates the non-secret roles required by a fresh directory database. */
@Component
public class RoleBootstrap {
    private final RoleRepository roleRepository;

    public RoleBootstrap(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeRoles() {
        ensureRole(RoleName.USER, "Standard user role");
        ensureRole(RoleName.ADMIN, "Administrator role");
    }

    private void ensureRole(RoleName roleName, String description) {
        if (roleRepository.findRoleByLabel(roleName).isEmpty()) {
            roleRepository.save(new Role(roleName).setDescription(description));
        }
    }
}
