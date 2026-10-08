package com.saas.directory.service;

import com.saas.directory.dto.PasswordChangeDto;
import com.saas.directory.dto.ProfileDto;
import com.saas.directory.dto.ProfileUpdateDto;

public interface AccountManager {
    /** Updates only the authenticated account, with current-password and version checks. */
    ProfileDto updateProfile(String username, ProfileUpdateDto update);
    /** Changes the password and revokes all existing session and recovery tokens atomically. */
    void changePassword(String username, PasswordChangeDto update);
}
