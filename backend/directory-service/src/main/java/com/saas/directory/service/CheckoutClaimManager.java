package com.saas.directory.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.dto.*;
import com.saas.directory.model.*;
import com.saas.directory.repository.*;

@Service
public class CheckoutClaimManager {
    private final CheckoutClaimRepository claims;
    private final GuestCustomerRepository customers;
    private final GuestSessionRepository sessions;
    private final UserRepository users;
    private final UserManager registration;
    private final GuestCheckoutManager guests;
    private final TokenManager tokens;
    private final PasswordEncoder passwords;
    private final PasswordResetNotificationSender notifications;
    private final RateLimitStore limits;
    private final String frontend;
    private final GuestOrderSessionRepository tracking;

    public CheckoutClaimManager(
            CheckoutClaimRepository claims,
            GuestCustomerRepository customers,
            GuestSessionRepository sessions,
            UserRepository users,
            UserManager registration,
            GuestCheckoutManager guests,
            TokenManager tokens,
            PasswordEncoder passwords,
            PasswordResetNotificationSender notifications,
            RateLimitStore limits,
            GuestOrderSessionRepository tracking,
            @Value("${saas.security.password-reset.frontend-url:}") String frontend) {
        this.claims = claims;
        this.customers = customers;
        this.sessions = sessions;
        this.users = users;
        this.registration = registration;
        this.guests = guests;
        this.tokens = tokens;
        this.passwords = passwords;
        this.notifications = notifications;
        this.limits = limits;
        this.tracking = tracking;
        this.frontend = frontend.replaceFirst("/reset-password/?$", "/claim-orders");
    }
    /** The public response is identical for existing, unknown and throttled addresses. */
    public void request(String input) {
        final String email = input.trim().toLowerCase(Locale.ROOT);
        if (frontend.isBlank()) return;
        if (!limits.tryAcquire("checkout-claim:" + GuestSecrets.digest(email), 3)) return;
        final Optional<GuestCustomer> customer = customers.findFirstByEmailOrderByCreatedAtDesc(email);
        if (customer.isEmpty()) return;
        final String secret = GuestSecrets.randomToken();
        claims.saveAndFlush(new CheckoutClaim(
                GuestSecrets.digest(secret), email, customer.get().getProfileJson(), Instant.now()));
        notifications.send(new PasswordResetNotification(email, frontend + "#token=" + secret, true));
    }

    public record Inspection(String email, boolean existingVerifiedAccount) {}

    @Transactional(readOnly = true)
    public Inspection inspect(String secret) {
        final CheckoutClaim claim = validOrDie(secret, false);
        return new Inspection(
                claim.getEmail(),
                users.findUserByUsernameIgnoreCase(claim.getEmail())
                        .map(User::isEmailConfirmed)
                        .orElse(false));
    }
    /** A mailbox proof never silently changes an established account's password. */
    @Transactional
    public void confirm(String secret, String password, String confirmation)
            throws javax.management.relation.RoleNotFoundException {
        final CheckoutClaim claim = validOrDie(secret, true);
        final Optional<User> existing = users.findByUsernameForUpdate(claim.getEmail());
        final User account;
        if (existing.isPresent()) {
            account = existing.get();
            if (!account.isActive()
                    || account.getRole() == null
                    || !account.getRole().isActive()) throw new IllegalArgumentException("Account unavailable");
            if (account.isEmailConfirmed()) {
                if (password == null || !passwords.matches(password, account.getPasswordHash()))
                    throw new IllegalArgumentException("Invalid credentials");
            } else {
                // Prior unverified credentials may belong to a pre-registration attacker.
                validatePassword(password, confirmation);
                account.setPasswordHash(passwords.encode(password));
                tokens.clearTokensOfUser(account);
            }
        } else {
            validatePassword(password, confirmation);
            account = registration.registerUser(
                    new UserRegistrationDto(claim.getEmail(), password, guests.readProfile(claim.getProfileJson())));
        }
        account.setEmailConfirmed(true);
        users.saveAndFlush(account);
        claim.consume(account.getId(), Instant.now());
        claims.saveAndFlush(claim);
        sessions.revokeForEmail(claim.getEmail(), claim.getCutoff());
    }
    /** Redeeming for tracking consumes the proof without creating an account. */
    @Transactional
    public void track(String proof, String sessionToken) {
        final CheckoutClaim claim = validOrDie(proof, true);
        tracking.save(new GuestOrderSession(GuestSecrets.digest(sessionToken), claim.getEmail(), claim.getCutoff()));
        claim.consume(null, Instant.now());
        claims.saveAndFlush(claim);
    }

    private void validatePassword(String password, String confirmation) {
        if (!Objects.equals(password, confirmation)) throw new IllegalArgumentException("Passwords must match");
        PasswordPolicy.validate(password);
    }

    private CheckoutClaim validOrDie(String secret, boolean lock) {
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid link");
        final String digest = GuestSecrets.digest(secret);
        return (lock ? claims.findByTokenDigestForUpdate(digest) : claims.findByTokenDigest(digest))
                .filter(c -> c.getConsumedAt() == null && c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid link"));
    }
}
