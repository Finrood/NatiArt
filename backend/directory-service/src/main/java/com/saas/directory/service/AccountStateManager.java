package com.saas.directory.service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.event.AccountStateChangedEvent;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;

/** Changes account authority and revokes its directory tokens in one transaction. */
@Service
public class AccountStateManager {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenManager tokenManager;
    private final ApplicationEventPublisher events;

    public AccountStateManager(
            UserRepository userRepository,
            RoleRepository roleRepository,
            TokenManager tokenManager,
            ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenManager = tokenManager;
        this.events = events;
    }

    @Transactional
    public User changeAccountState(String userId, Boolean active, RoleName roleName) {
        if (active == null && roleName == null) {
            throw new IllegalArgumentException("An account change must specify active or role");
        }
        final User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (active != null && active != user.isActive()) {
            user.setActive(active).setDeactivatedAt(active ? null : Instant.now());
        }
        if (roleName != null && user.getRole().getLabel() != roleName) {
            final Role role = roleRepository
                    .findRoleByLabel(roleName)
                    .filter(Role::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Requested role is unavailable"));
            user.setRole(role);
        }
        // Repeating a request also revokes sessions and retries invalidation after a
        // transient callback failure. This makes the supported operation safe to retry.
        userRepository.save(user);
        tokenManager.clearTokensOfUser(user);
        events.publishEvent(new AccountStateChangedEvent(user.getId()));
        return user;
    }
}
