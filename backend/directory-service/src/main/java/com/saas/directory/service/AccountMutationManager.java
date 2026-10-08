package com.saas.directory.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.controller.helper.AccountChangeRejectedException;
import com.saas.directory.controller.helper.AccountChangeRejectedException.Reason;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.*;
import com.saas.directory.dto.asaas.AsaasCustomerUpdateRequest;
import com.saas.directory.event.AccountStateChangedEvent;
import com.saas.directory.model.*;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.*;

@Service
public class AccountMutationManager {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ExternalUserRepository externalUsers;
    private final AsaasProvisioningJobRepository jobs;
    private final ProfileManager profileManager;
    private final AsaasUserManager asaas;
    private final PasswordEncoder passwords;
    private final TokenManager tokens;
    private final ApplicationEventPublisher events;

    public AccountMutationManager(
            UserRepository users,
            ProfileRepository profiles,
            ExternalUserRepository externalUsers,
            AsaasProvisioningJobRepository jobs,
            ProfileManager profileManager,
            AsaasUserManager asaas,
            PasswordEncoder passwords,
            TokenManager tokens,
            ApplicationEventPublisher events) {
        this.users = users;
        this.profiles = profiles;
        this.externalUsers = externalUsers;
        this.jobs = jobs;
        this.profileManager = profileManager;
        this.asaas = asaas;
        this.passwords = passwords;
        this.tokens = tokens;
        this.events = events;
    }

    /** Saves an authorized draft while serializing profile and provisioning changes. */
    @Transactional
    public ProfileDto updateProfile(String username, ProfileUpdateDto update) {
        if (update == null || update.profile() == null) throw new IllegalArgumentException("Profile is required");
        final User user = accountOrDie(username);
        verifyPassword(user, update.currentPassword());
        final Profile current = user.getProfile();
        if (current == null) throw new ResourceNotFoundException("Profile not found");
        if (update.profile().getVersion() == null || update.profile().getVersion() != current.getVersion()) {
            throw new AccountChangeRejectedException(Reason.PROFILE_CONFLICT);
        }
        final Profile next = profileManager.buildProfile(user, update.profile());
        final AsaasProvisioningJob job =
                jobs.findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS).orElse(null);
        if (job != null && job.getStatus() == AsaasProvisioningStatus.IN_PROGRESS) {
            throw new AccountChangeRejectedException(Reason.ACCOUNT_SETUP_IN_PROGRESS);
        }
        final ExternalUser customer = externalUsers
                .findByUserAndPaymentProcessor(user, PaymentProcessor.ASAAS)
                .orElse(null);
        if (customer != null) {
            try {
                asaas.updateCustomer(
                        customer.getExternalId(),
                        AsaasCustomerUpdateRequest.from(user.getId(), user.getUsername(), ProfileDto.from(next)));
            } catch (RuntimeException failure) {
                throw new AccountChangeRejectedException(Reason.PROVIDER_UNAVAILABLE);
            }
        } else if (job == null) {
            jobs.save(new AsaasProvisioningJob(user, PaymentProcessor.ASAAS, Instant.now()));
        } else {
            job.retryAt(Instant.now(), "Customer details updated");
        }
        current.setFirstname(next.getFirstname())
                .setLastname(next.getLastname())
                .setCpf(next.getCpf())
                .setPhone(next.getPhone())
                .setCountry(next.getCountry())
                .setState(next.getState())
                .setCity(next.getCity())
                .setNeighborhood(next.getNeighborhood())
                .setZipCode(next.getZipCode())
                .setStreet(next.getStreet())
                .setHouseNumber(next.getHouseNumber())
                .setComplement(next.getComplement());
        return ProfileDto.from(profiles.saveAndFlush(current));
    }

    /** Rechecks the current password under the same lock used by token issuance. */
    @Transactional
    public void changePassword(String username, PasswordChangeDto update) {
        if (update == null || !Objects.equals(update.password(), update.passwordConfirmation())) {
            throw new IllegalArgumentException("Passwords must be identical");
        }
        PasswordPolicy.validate(update.password());
        final User user = accountOrDie(username);
        verifyPassword(user, update.currentPassword());
        user.setPasswordHash(passwords.encode(update.password()));
        users.save(user);
        tokens.clearTokensOfUser(user);
        events.publishEvent(new AccountStateChangedEvent(user.getId()));
    }

    private User accountOrDie(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Account is required");
        return users.findByUsernameForUpdate(username)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private void verifyPassword(User user, String currentPassword) {
        if (currentPassword == null
                || currentPassword.length() > 256
                || !passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new AccountChangeRejectedException(Reason.CURRENT_PASSWORD_INCORRECT);
        }
    }
}
