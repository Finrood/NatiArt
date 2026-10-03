package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.saas.directory.event.AccountStateChangedEvent;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;

class AccountStateManagerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final TokenManager tokens = mock(TokenManager.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AccountStateManager manager = new AccountStateManager(users, roles, tokens, events);

    @Test
    void downgradeRevokesSessionsAndEmitsInvalidationForTheSameUser() {
        final User user = new User("admin@example.test", "password").setRole(new Role(RoleName.ADMIN));
        final Role userRole = new Role(RoleName.USER);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(roles.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(userRole));

        manager.changeAccountState(user.getId(), null, RoleName.USER);

        assertEquals(userRole, user.getRole());
        verify(users).save(user);
        verify(tokens).clearTokensOfUser(user);
        verify(events).publishEvent(new AccountStateChangedEvent(user.getId()));
    }

    @Test
    void disableAndRepeatedDisableBothRevokeSessionsAndRetryInvalidation() {
        final User user = new User("buyer@example.test", "password").setRole(new Role(RoleName.USER));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        manager.changeAccountState(user.getId(), false, null);
        manager.changeAccountState(user.getId(), false, null);

        assertFalse(user.isActive());
        org.mockito.Mockito.verify(tokens, org.mockito.Mockito.times(2)).clearTokensOfUser(user);
        org.mockito.Mockito.verify(events, org.mockito.Mockito.times(2))
                .publishEvent(new AccountStateChangedEvent(user.getId()));
    }

    @Test
    void unavailableRoleCannotChangeAccountOrRevokeSessions() {
        final User user = new User("buyer@example.test", "password").setRole(new Role(RoleName.USER));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(roles.findRoleByLabel(RoleName.ADMIN)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class, () -> manager.changeAccountState(user.getId(), null, RoleName.ADMIN));
        verify(users, never()).save(user);
        verify(tokens, never()).clearTokensOfUser(user);
    }
}
