package com.saas.directory.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.saas.directory.controller.helper.AccountChangeRejectedException;
import com.saas.directory.controller.helper.AccountChangeRejectedException.Reason;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.PasswordChangeDto;
import com.saas.directory.dto.ProfileDto;
import com.saas.directory.dto.ProfileUpdateDto;
import com.saas.directory.model.User;

@Service
public class AccountManagerImpl implements AccountManager {
    private final UserManager users;
    private final AccountChangeThrottle throttle;
    private final AccountMutationManager mutations;

    public AccountManagerImpl(UserManager users, AccountChangeThrottle throttle, AccountMutationManager mutations) {
        this.users = users;
        this.throttle = throttle;
        this.mutations = mutations;
    }

    @Override
    public ProfileDto updateProfile(String username, ProfileUpdateDto update) {
        acquireAttempt(username);
        return mutations.updateProfile(username, update);
    }

    @Override
    public void changePassword(String username, PasswordChangeDto update) {
        acquireAttempt(username);
        mutations.changePassword(username, update);
    }

    private void acquireAttempt(String username) {
        final User user = users.getUserOrDie(username);
        if (!user.isActive()) throw new ResourceNotFoundException("Account not found");
        // Commit the attempt before taking account locks, so edits use only one pool connection.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (!throttle.acquire(user.getId())) throw new AccountChangeRejectedException(Reason.TRY_LATER);
                return;
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException conflict) {
                if (attempt == 2) throw conflict;
            }
        }
    }
}
