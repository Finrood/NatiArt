package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.saas.directory.dto.*;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.model.*;
import com.saas.directory.repository.*;

@SpringBootTest(
        properties = {
            "saas.security.password-reset.frontend-url=http://localhost:4200/en/reset-password",
            "natiart.guest.claim-delivery-delay-millis=3600000",
            "saas.asaas.provisioning.fixed-delay-millis=3600000"
        })
class GuestCheckoutContractTest {
    @Autowired
    private GuestCheckoutManager guests;

    @Autowired
    private CheckoutClaimManager claims;

    @Autowired
    private GuestOrderSessionManager tracking;

    @Autowired
    private GuestSessionRepository sessions;

    @Autowired
    private GuestCustomerRepository customers;

    @Autowired
    private CheckoutClaimRepository proofs;

    @Autowired
    private UserRepository users;

    @Autowired
    private TokenRepository tokens;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private AsaasProvisioningService provisioning;

    @MockitoBean
    private AsaasUserManager provider;

    @MockitoBean
    private PasswordResetNotificationSender mail;

    private ProfileDto profile() {
        return new ProfileDto()
                .setFirstname("Guest")
                .setLastname("Buyer")
                .setCpf("52998224725")
                .setPhone("11999999999")
                .setCountry("Brazil")
                .setState("SP")
                .setCity("Sao Paulo")
                .setNeighborhood("Centro")
                .setZipCode("01001000")
                .setStreet("Praca da Se")
                .setHouseNumber("10");
    }

    private String email() {
        return "guest-" + UUID.randomUUID() + "@example.test";
    }

    private GuestSessionDto details(String token, String email) {
        final GuestSessionDto session = guests.create(token);
        return guests.saveDetails(token, session.csrfToken(), new GuestDetailsDto(email, profile(), true));
    }

    private String proof(String email) {
        claims.request(email);
        final ArgumentCaptor<PasswordResetNotification> sent = ArgumentCaptor.forClass(PasswordResetNotification.class);
        verify(mail, atLeastOnce()).send(sent.capture());
        final PasswordResetNotification notification = sent.getAllValues().stream()
                .filter(value -> value.recipient().equals(email))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertTrue(notification.checkoutClaim());
        assertTrue(notification.resetLink().startsWith("http://localhost:4200/en/claim-orders#token="));
        return notification.resetLink().split("#token=")[1];
    }

    private User account(String email, boolean verified) {
        final Role role = roles.findAll().stream()
                .filter(value -> value.getLabel() == RoleName.USER)
                .findFirst()
                .orElseGet(() -> roles.saveAndFlush(new Role(RoleName.USER)));
        return users.saveAndFlush(
                new User(email, "OriginalPassword1!").setRole(role).setEmailConfirmed(verified));
    }

    @Test
    void checkoutDoesNotCreateAnAccountOrOverwriteAnExistingEmail() {
        final String email = email();
        final User original = account(email, true);
        final long count = users.count();
        final String token = GuestSecrets.randomToken();
        final GuestSessionDto saved = details(token, email.toUpperCase());
        assertEquals(count, users.count());
        assertEquals(email, saved.email());
        assertNotEquals(original.getId(), saved.customerId());
        assertEquals(
                original.getPasswordHash(),
                users.findById(original.getId()).orElseThrow().getPasswordHash());
        assertTrue(sessions.findByTokenDigest(GuestSecrets.digest(token)).isPresent());
        assertTrue(sessions.findByTokenDigest(token).isEmpty());
        assertTrue(saved.expiresAt().isAfter(Instant.now().plusSeconds(29L * 86400)));
        assertThrows(
                AccessDeniedException.class,
                () -> guests.saveDetails(token, "wrong", new GuestDetailsDto(email, profile(), true)));
        guests.forget(token, saved.csrfToken());
        assertThrows(AccessDeniedException.class, () -> guests.validateOrDie(token, null, false));
        assertTrue(customers.findById(saved.customerId()).isPresent());
    }

    @Test
    void oldPaymentCompletionCannotClearANewerDraft() {
        final String token = GuestSecrets.randomToken();
        final GuestSessionDto saved = details(token, email());
        final String attempt = "{\"username\":\"guest:" + saved.id() + "\",\"currentOrder\":{\"id\":\"new-order\"}}";
        guests.saveAttempt(token, saved.csrfToken(), attempt, null, null);
        guests.saveAttempt(token, saved.csrfToken(), null, "old-order", null);
        assertEquals(attempt, guests.validateOrDie(token, null, false).attemptJson());
        assertThrows(
                IllegalArgumentException.class,
                () -> guests.saveDetails(token, saved.csrfToken(), new GuestDetailsDto(email(), profile(), true)));
        guests.saveAttempt(token, saved.csrfToken(), null, "new-order", null);
        assertNull(guests.validateOrDie(token, null, false).attemptJson());
    }

    @Test
    void trackingConsumesEmailProofWithoutCreatingAnAccount() {
        final String email = email();
        final String token = GuestSecrets.randomToken();
        details(token, email);
        final long count = users.count();
        final String proof = proof(email);
        final String readToken = GuestSecrets.randomToken();
        claims.track(proof, readToken);
        assertEquals(count, users.count());
        assertEquals(email, tracking.validateOrDie(readToken).email());
        assertThrows(IllegalArgumentException.class, () -> claims.inspect(proof));
        assertThrows(AccessDeniedException.class, () -> guests.validateOrDie(readToken, null, false));
        assertThrows(AccessDeniedException.class, () -> tracking.validateOrDie(token));
        assertFalse(
                proofs.findDueIds(Instant.now().plusSeconds(1), org.springframework.data.domain.PageRequest.of(0, 100))
                        .contains(proofs.findByTokenDigest(GuestSecrets.digest(proof))
                                .orElseThrow()
                                .getId()));
    }

    @Test
    void verifiedAccountRequiresItsCurrentPasswordAndKeepsCredentials() throws Exception {
        final String email = email();
        final User account = account(email, true);
        final String token = GuestSecrets.randomToken();
        details(token, email);
        final String proof = proof(email);
        assertTrue(claims.inspect(proof).existingVerifiedAccount());
        assertThrows(
                IllegalArgumentException.class,
                () -> claims.confirm(proof, "DifferentPassword1!", "DifferentPassword1!"));
        claims.confirm(proof, "OriginalPassword1!", "OriginalPassword1!");
        assertEquals(
                account.getPasswordHash(),
                users.findById(account.getId()).orElseThrow().getPasswordHash());
        assertThrows(AccessDeniedException.class, () -> guests.validateOrDie(token, null, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> claims.confirm(proof, "OriginalPassword1!", "OriginalPassword1!"));
    }

    @Test
    void verifiedMailboxReplacesUnverifiedCredentialsAndDoesNotReactivateDisabledAccounts() throws Exception {
        final String email = email();
        final User account = account(email, false);
        details(GuestSecrets.randomToken(), email);
        final String oldToken = "old-session-" + UUID.randomUUID();
        tokens.saveAndFlush(new Token(
                oldToken, account, TokenType.AUTH_ACCESS, Instant.now().plusSeconds(600)));
        final String proof = proof(email);
        claims.confirm(proof, "NewPassword123!", "NewPassword123!");
        final User updated = users.findById(account.getId()).orElseThrow();
        assertTrue(tokens.findByJti(oldToken).isEmpty());
        assertTrue(updated.isEmailConfirmed());
        assertTrue(passwords.matches("NewPassword123!", updated.getPasswordHash()));
        assertFalse(passwords.matches("OriginalPassword1!", updated.getPasswordHash()));
        final String disabledEmail = email();
        final User disabled = account(disabledEmail, false);
        disabled.setActive(false);
        users.saveAndFlush(disabled);
        details(GuestSecrets.randomToken(), disabledEmail);
        final String disabledProof = proof(disabledEmail);
        assertThrows(
                IllegalArgumentException.class,
                () -> claims.confirm(disabledProof, "NewPassword123!", "NewPassword123!"));
        assertFalse(users.findById(disabled.getId()).orElseThrow().isActive());
    }

    @Test
    void concurrentRedemptionsCreateExactlyOneReadSession() throws Exception {
        final String email = email();
        details(GuestSecrets.randomToken(), email);
        final String proof = proof(email);
        try (final ExecutorService pool = Executors.newFixedThreadPool(2)) {
            final CountDownLatch start = new CountDownLatch(1);
            final Callable<Boolean> consume = () -> {
                start.await();
                try {
                    claims.track(proof, GuestSecrets.randomToken());
                    return true;
                } catch (IllegalArgumentException used) {
                    return false;
                }
            };
            final Future<Boolean> a = pool.submit(consume);
            final Future<Boolean> b = pool.submit(consume);
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void provisionedGuestCannotBeMappedToAnotherPayerAndHttpRunsOutsideTransaction() throws Exception {
        final String token = GuestSecrets.randomToken();
        final GuestSessionDto saved = details(token, email());
        when(provider.findCustomersByExternalReference(saved.customerId())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of();
        });
        final AsaasCustomerCreationResponse wrong = mock(AsaasCustomerCreationResponse.class);
        when(wrong.getId()).thenReturn("cus_wrong");
        when(wrong.getExternalReference()).thenReturn("someone-else");
        when(wrong.getCpfCnpj()).thenReturn("52998224725");
        when(provider.registerCustomer(any(UserDto.class), eq(true))).thenReturn(wrong);
        provisioning.provisionGuest(saved.customerId());
        final GuestSessionDto after = guests.validateOrDie(token, null, false);
        assertNull(after.externalId());
        assertEquals(AsaasProvisioningStatus.FAILED, after.provisioningStatus());
    }

    @Test
    void expiredProofAndExpiredGuestSessionFailClosed() {
        final String token = GuestSecrets.randomToken();
        sessions.saveAndFlush(
                new GuestSession(GuestSecrets.digest(token), Instant.now().minusSeconds(1)));
        assertThrows(AccessDeniedException.class, () -> guests.validateOrDie(token, null, false));
        final String proof = GuestSecrets.randomToken();
        proofs.saveAndFlush(new CheckoutClaim(
                GuestSecrets.digest(proof), email(), "{}", Instant.now().minusSeconds(901)));
        assertThrows(IllegalArgumentException.class, () -> claims.inspect(proof));
    }
}
