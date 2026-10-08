package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.saas.directory.controller.helper.AccountChangeRejectedException;
import com.saas.directory.controller.helper.AccountChangeRejectedException.Reason;
import com.saas.directory.dto.PasswordChangeDto;
import com.saas.directory.dto.ProfileDto;
import com.saas.directory.dto.ProfileUpdateDto;
import com.saas.directory.model.User;

@ExtendWith(MockitoExtension.class)
class AccountManagerImplTest {
    @Mock
    private UserManager users;

    @Mock
    private AccountChangeThrottle throttle;

    @Mock
    private AccountMutationManager mutations;

    @InjectMocks
    private AccountManagerImpl accounts;

    @Test
    void updateProfileAcquiresCommittedAttemptBeforeStartingMutation() {
        final User user = new User("ana@example.test", "OldPassword123");
        final ProfileUpdateDto update = new ProfileUpdateDto(new ProfileDto(), "OldPassword123");
        final ProfileDto saved = new ProfileDto();
        when(users.getUserOrDie(user.getUsername())).thenReturn(user);
        when(throttle.acquire(user.getId())).thenReturn(true);
        when(mutations.updateProfile(user.getUsername(), update)).thenReturn(saved);

        assertEquals(saved, accounts.updateProfile(user.getUsername(), update));

        final InOrder order = inOrder(throttle, mutations);
        order.verify(throttle).acquire(user.getId());
        order.verify(mutations).updateProfile(user.getUsername(), update);
    }

    @Test
    void passwordChangeRetriesConcurrentFirstCounterInsertBeforeMutation() {
        final User user = new User("ana@example.test", "OldPassword123");
        final PasswordChangeDto update = new PasswordChangeDto("OldPassword123", "NewPassword456", "NewPassword456");
        when(users.getUserOrDie(user.getUsername())).thenReturn(user);
        when(throttle.acquire(user.getId()))
                .thenThrow(new DataIntegrityViolationException("concurrent insert"))
                .thenReturn(true);

        accounts.changePassword(user.getUsername(), update);

        final InOrder order = inOrder(throttle, mutations);
        order.verify(throttle, times(2)).acquire(user.getId());
        order.verify(mutations).changePassword(user.getUsername(), update);
    }

    @Test
    void exhaustedAccountAttemptLimitNeverStartsPasswordMutation() {
        final User user = new User("ana@example.test", "OldPassword123");
        when(users.getUserOrDie(user.getUsername())).thenReturn(user);
        when(throttle.acquire(user.getId())).thenReturn(false);

        final AccountChangeRejectedException rejected = assertThrows(
                AccountChangeRejectedException.class,
                () -> accounts.changePassword(
                        user.getUsername(),
                        new PasswordChangeDto("OldPassword123", "NewPassword456", "NewPassword456")));

        assertEquals(Reason.TRY_LATER, rejected.getReason());
        verifyNoInteractions(mutations);
    }
}
