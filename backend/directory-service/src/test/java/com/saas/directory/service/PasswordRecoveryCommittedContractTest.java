package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.controller.PasswordResetController;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.dto.ResetPasswordDto;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.model.*;
import com.saas.directory.repository.*;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "natiart.test.contract=password-recovery"})
@Import({
    PasswordManager.class,
    TokenManager.class,
    AuthenticationManager.class,
    UserAuthenticationProvider.class,
    PasswordRecoveryCommittedContractTest.Encoding.class
})
class PasswordRecoveryCommittedContractTest {
    @Autowired
    private PasswordManager manager;

    @Autowired
    private AuthenticationManager authentication;

    @Autowired
    private UserAuthenticationProvider provider;

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private ExternalUserRepository externalUsers;

    @Autowired
    private TokenRepository tokens;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private UserManager userManager;

    @MockitoBean
    private RateLimitStore limiter;

    @MockitoBean
    private PasswordResetNotificationSender sender;

    private TransactionTemplate transaction;

    @TestConfiguration
    static class Encoding {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @BeforeEach
    void configure() {
        transaction = new TransactionTemplate(transactions);
        when(limiter.tryAcquire(anyString(), eq(3))).thenReturn(true);
        when(userManager.getUser(anyString()))
                .thenAnswer(call -> users.findUserByUsernameIgnoreCase(call.getArgument(0)));
        when(userManager.getUserOrDie(anyString()))
                .thenAnswer(call ->
                        users.findUserByUsernameIgnoreCase(call.getArgument(0)).orElseThrow());
    }

    private User seed() {
        return transaction.execute(status -> {
            final Role role = roles.findAll().stream().findFirst().orElseGet(() -> roles.save(new Role(RoleName.USER)));
            final User saved =
                    users.save(new User("fixture-" + UUID.randomUUID() + "@example.test", "OldPass123").setRole(role));
            externalUsers.save(new ExternalUser(
                    saved, com.saas.directory.model.helper.PaymentProcessor.ASAAS, "customer-" + saved.getId()));
            return saved;
        });
    }

    private String resetToken(User user, Instant expiry) {
        final String token = UUID.randomUUID().toString();
        transaction.executeWithoutResult(
                status -> tokens.save(new Token(token, user, TokenType.PASSWORD_RESET, expiry)));
        return token;
    }

    private void redeem(String token) {
        manager.doResetPassword(token, new ResetPasswordDto("NewPass123", "NewPass123"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void revokedBrowserAccessOrRefreshCannotBlockAnonymousRecovery(boolean refreshBearer) throws Exception {
        final User user = seed();
        final UserAuthDto session = authentication.login(new CredentialsDto(user.getUsername(), "OldPass123"));
        transaction.executeWithoutResult(status -> tokens.deleteAll());
        final String rejected = refreshBearer ? session.getRefreshToken() : session.getAccessToken();
        assertThrows(
                IllegalAccessException.class,
                () -> provider.authenticateWithToken(
                        rejected, refreshBearer ? TokenType.AUTH_REFRESH : TokenType.AUTH_ACCESS));
        final MockMvc http = MockMvcBuilders.standaloneSetup(new PasswordResetController(manager))
                .addFilters(new com.saas.directory.configuration.JwtAuthFilter(provider))
                .build();
        http.perform(post("/password-reset/request")
                        .header("Authorization", "Bearer " + rejected)
                        .contentType("application/json")
                        .content("{\"username\":\"" + user.getUsername() + "\"}"))
                .andExpect(status().isAccepted());
        final String token = resetToken(user, Instant.now().plusSeconds(900));
        http.perform(post("/password-reset")
                        .header("Authorization", "Bearer " + rejected)
                        .contentType("application/json")
                        .content("{\"token\":\"" + token
                                + "\",\"password\":\"NewPass123\",\"passwordConfirmation\":\"NewPass123\"}"))
                .andExpect(status().isNoContent());
        assertTrue(encoder.matches(
                "NewPass123", users.findById(user.getId()).orElseThrow().getPasswordHash()));
        assertTrue(tokens.findByJtiAndTokenType(token, TokenType.PASSWORD_RESET).isEmpty());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void actualSmtpDeliveryContainsUsableFragmentLinkAndUniformRequestContract() throws Exception {
        final User user = seed();
        try (SmtpFixture smtp = new SmtpFixture()) {
            final JavaMailSenderImpl mail = new JavaMailSenderImpl();
            mail.setHost("127.0.0.1");
            mail.setPort(smtp.port());
            mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "5000");
            final PasswordResetNotificationSender delivery =
                    new SmtpPasswordResetNotificationSender(mail, "recovery@example.test");
            doAnswer(call -> {
                        delivery.send(call.getArgument(0));
                        return null;
                    })
                    .when(sender)
                    .send(any());
            final MockMvc http = MockMvcBuilders.standaloneSetup(new PasswordResetController(manager))
                    .build();
            final String known = http.perform(post("/password-reset/request")
                            .contentType("application/json")
                            .content("{\"username\":\"" + user.getUsername() + "\"}"))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            final String unknown = http.perform(post("/password-reset/request")
                            .contentType("application/json")
                            .content("{\"username\":\"unknown@example.test\"}"))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertEquals(known, unknown);
            final MimeMessage message = smtp.message();
            assertEquals(user.getUsername(), message.getAllRecipients()[0].toString());
            assertEquals("recovery@example.test", message.getFrom()[0].toString());
            final String body = (String) message.getContent();
            final var match = Pattern.compile("#token=([0-9a-f-]{36})").matcher(body);
            assertTrue(match.find());
            redeem(match.group(1));
            assertTrue(encoder.matches(
                    "NewPass123", users.findById(user.getId()).orElseThrow().getPasswordHash()));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiredAndUsedTokensCannotChangePasswordAndSuccessfulResetRevokesEverySession() throws Exception {
        final User user = seed();
        final UserAuthDto before = authentication.login(new CredentialsDto(user.getUsername(), "OldPass123"));
        final String expired = resetToken(user, Instant.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> redeem(expired));
        assertTrue(encoder.matches(
                "OldPass123", users.findById(user.getId()).orElseThrow().getPasswordHash()));
        final String valid = resetToken(user, Instant.now().plusSeconds(900));
        transaction.executeWithoutResult(status -> {
            tokens.save(new Token(
                    UUID.randomUUID().toString(),
                    user,
                    TokenType.AUTH_ACCESS,
                    Instant.now().plusSeconds(900)));
            tokens.save(new Token(
                    UUID.randomUUID().toString(),
                    user,
                    TokenType.AUTH_REFRESH,
                    Instant.now().plusSeconds(900)));
        });
        redeem(valid);
        assertThrows(IllegalArgumentException.class, () -> redeem(valid));
        assertThrows(
                IllegalAccessException.class,
                () -> provider.authenticateWithToken(before.getAccessToken(), TokenType.AUTH_ACCESS));
        assertThrows(
                IllegalAccessException.class,
                () -> provider.authenticateWithToken(before.getRefreshToken(), TokenType.AUTH_REFRESH));
        assertThrows(
                com.saas.directory.controller.helper.ResourceNotFoundException.class,
                () -> authentication.login(new CredentialsDto(user.getUsername(), "OldPass123")));

        assertEquals(
                0,
                tokens.findAll().stream()
                        .filter(token -> token.getUser().getId().equals(user.getId()))
                        .count());
        final UserAuthDto after = authentication.login(new CredentialsDto(user.getUsername(), "NewPass123"));
        final var resumed = provider.authenticateWithToken(after.getAccessToken(), TokenType.AUTH_ACCESS);
        final var identity = (com.saas.directory.dto.UserDto) resumed.getPrincipal();
        assertEquals(user.getId(), identity.getId());
        assertEquals("customer-" + user.getId(), identity.getExternalId());
        assertTrue(encoder.matches(
                "NewPass123", users.findById(user.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void independentConcurrentTransactionsAllowExactlyOneRedemption() throws Exception {
        final User user = seed();
        final String token = resetToken(user, Instant.now().plusSeconds(900));
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            final Callable<Boolean> attempt = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    redeem(token);
                    return true;
                } catch (IllegalArgumentException rejected) {
                    return false;
                }
            };
            final Future<Boolean> first = executor.submit(attempt);
            final Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertTrue(tokens.findByJtiAndTokenType(token, TokenType.PASSWORD_RESET).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void newRequestInvalidatesEarlierResetLinkWithoutRevokingCurrentLogin() {
        final User user = seed();
        final String old = resetToken(user, Instant.now().plusSeconds(900));
        final String access = UUID.randomUUID().toString();
        transaction.executeWithoutResult(status -> tokens.save(
                new Token(access, user, TokenType.AUTH_ACCESS, Instant.now().plusSeconds(900))));
        manager.notifyResetPassword(user.getUsername());
        assertTrue(tokens.findByJtiAndTokenType(old, TokenType.PASSWORD_RESET).isEmpty());
        assertTrue(tokens.findByJtiAndTokenType(access, TokenType.AUTH_ACCESS).isPresent());
        verify(sender).send(any());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void wrongPurposeAndOverlongPasswordsCannotConsumeAValidResetToken() {
        final User user = seed();
        final String wrong = UUID.randomUUID().toString();
        transaction.executeWithoutResult(status -> tokens.save(
                new Token(wrong, user, TokenType.AUTH_REFRESH, Instant.now().plusSeconds(900))));
        assertThrows(IllegalArgumentException.class, () -> redeem(wrong));
        final String valid = resetToken(user, Instant.now().plusSeconds(900));
        final String overlong = "Ab1" + "é".repeat(35);
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.doResetPassword(valid, new ResetPasswordDto(overlong, overlong)));
        assertTrue(tokens.findByJtiAndTokenType(valid, TokenType.PASSWORD_RESET).isPresent());
        assertTrue(encoder.matches(
                "OldPass123", users.findById(user.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedSmtpDeliveryRollsBackTokenReplacementAndStillReturnsUniformAcceptedResponse() throws Exception {
        final User user = seed();
        final String old = resetToken(user, Instant.now().plusSeconds(900));
        try (SmtpFixture smtp = new SmtpFixture(true)) {
            final JavaMailSenderImpl mail = new JavaMailSenderImpl();
            mail.setHost("127.0.0.1");
            mail.setPort(smtp.port());
            mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            final PasswordResetNotificationSender delivery =
                    new SmtpPasswordResetNotificationSender(mail, "recovery@example.test");
            doAnswer(call -> {
                        delivery.send(call.getArgument(0));
                        return null;
                    })
                    .when(sender)
                    .send(any());
            final MockMvc http = MockMvcBuilders.standaloneSetup(new PasswordResetController(manager))
                    .build();
            final String failed = http.perform(post("/password-reset/request")
                            .contentType("application/json")
                            .content("{\"username\":\"" + user.getUsername() + "\"}"))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            final String unknown = http.perform(post("/password-reset/request")
                            .contentType("application/json")
                            .content("{\"username\":\"unknown@example.test\"}"))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertEquals(unknown, failed);
            assertTrue(
                    tokens.findByJtiAndTokenType(old, TokenType.PASSWORD_RESET).isPresent());
            assertFalse(failed.contains("#token="));
        }
    }

    // An isolated loopback SMTP peer exercises JavaMail's actual envelope and DATA path.
    private static final class SmtpFixture implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<String> received;

        SmtpFixture() throws IOException {
            this(false);
        }

        SmtpFixture(boolean reject) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            received = executor.submit(() -> {
                try (Socket socket = server.accept();
                        BufferedReader input = new BufferedReader(
                                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        PrintWriter output =
                                new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII)) {
                    socket.setSoTimeout(5000);
                    output.print("220 localhost fixture\r\n");
                    output.flush();
                    String message = null;
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (line.startsWith("DATA")) {
                            output.print("354 send data\r\n");
                            output.flush();
                            final StringBuilder body = new StringBuilder();
                            while ((line = input.readLine()) != null && !line.equals(".")) {
                                body.append(line.startsWith("..") ? line.substring(1) : line)
                                        .append("\r\n");
                            }
                            message = body.toString();
                            output.print(reject ? "550 delivery rejected\r\n" : "250 accepted\r\n");
                        } else if (line.startsWith("QUIT")) {
                            output.print("221 bye\r\n");
                            output.flush();
                            break;
                        } else {
                            output.print("250 localhost\r\n");
                        }
                        output.flush();
                    }
                    return message;
                }
            });
        }

        int port() {
            return server.getLocalPort();
        }

        MimeMessage message() throws Exception {
            return new MimeMessage(
                    Session.getInstance(new Properties()),
                    new ByteArrayInputStream(received.get(5, TimeUnit.SECONDS).getBytes(StandardCharsets.US_ASCII)));
        }

        @Override
        public void close() throws IOException {
            server.close();
            executor.shutdownNow();
        }
    }
}
