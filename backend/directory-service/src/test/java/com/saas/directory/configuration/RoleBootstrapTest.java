package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.repository.RoleRepository;

class RoleBootstrapTest {

    @Test
    void createsEachRequiredRoleOnceWhenRunRepeatedly() {
        final RoleRepository roleRepository = mock(RoleRepository.class);
        final Map<RoleName, Role> roles = new EnumMap<>(RoleName.class);
        when(roleRepository.findRoleByLabel(any(RoleName.class)))
                .thenAnswer(invocation -> Optional.ofNullable(roles.get(invocation.getArgument(0))));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            final Role role = invocation.getArgument(0);
            roles.put(role.getLabel(), role);
            return role;
        });

        final RoleBootstrap bootstrap = new RoleBootstrap(roleRepository);
        bootstrap.initializeRoles();
        bootstrap.initializeRoles();

        assertEquals(2, roles.size());
        verify(roleRepository, times(2)).save(any(Role.class));
    }
}
