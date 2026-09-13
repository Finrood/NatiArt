package com.saas.directory.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.saas.directory.dto.ResetPasswordDto;
import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.UserRepository;

@Service
public class PasswordManager {
    private final UserManager userManager;
    private final UserRepository userRepository;
    private final TokenManager tokenManager;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitStore rateLimitStore;
    private final PasswordResetNotificationSender notificationSender;
    private final String resetFrontendUrl;

    public PasswordManager(
            UserManager userManager,
            UserRepository userRepository,
            TokenManager tokenManager,
            PasswordEncoder passwordEncoder,
            RateLimitStore rateLimitStore,
            PasswordResetNotificationSender notificationSender,
            @Value("${saas.security.password-reset.frontend-url:http://localhost:4200/reset-password}")
                    String resetFrontendUrl) {
        this.userManager = userManager;
        this.userRepository = userRepository;
        this.tokenManager = tokenManager;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitStore = rateLimitStore;
        this.notificationSender = notificationSender;
        this.resetFrontendUrl = resetFrontendUrl == null || resetFrontendUrl.isBlank()
                ? "http://localhost:4200/reset-password"
                : resetFrontendUrl;
    }

    @Transactional(readOnly = true)
    public Token getPasswordResetTokenOrDie(String jti) {
        return tokenManager.getTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET);
    }

    @Transactional(readOnly = true)
    public Token getValidPasswordResetTokenOrDie(String jti) {
        return tokenManager.getValidTokenByJtiAndTokenTypeOrDie(jti, TokenType.PASSWORD_RESET);
    }

    @Transactional
    public void notifyResetPassword(String username) {
        final String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }
        if (!rateLimitStore.tryAcquire(rateLimitKey(normalizedUsername), 3)) {
            return;
        }

        userManager.getUser(normalizedUsername).ifPresent(user -> {
            tokenManager.clearTokensOfUserByType(user, TokenType.PASSWORD_RESET);
            final Token token = tokenManager.generateRandomUUIDToken(
                    user, 15, ChronoUnit.MINUTES, TokenType.PASSWORD_RESET);
            notificationSender.send(new PasswordResetNotification(user.getUsername(), resetLink(token.getJti())));
        });
    }

    @Transactional
    public void doResetPassword(String jti, ResetPasswordDto resetPasswordDto) {
        if (resetPasswordDto == null || !Objects.equals(resetPasswordDto.password(), resetPasswordDto.passwordConfirmation())) {
            throw new IllegalArgumentException("Passwords must be identical");
        }
        PasswordPolicy.validate(resetPasswordDto.password());

        final Token passwordResetToken = getValidPasswordResetTokenOrDie(jti);
        if (!tokenManager.consumeValidToken(jti, TokenType.PASSWORD_RESET)) {
            throw new IllegalArgumentException("Invalid token");
        }

        final User user = passwordResetToken.getUser();

        user.setPasswordHash(passwordEncoder.encode(resetPasswordDto.password()));
        userRepository.save(user);
        tokenManager.clearTokensOfUser(user);
    }

    private String resetLink(String jti) {
        final String base = resetFrontendUrl.endsWith("/")
                ? resetFrontendUrl.substring(0, resetFrontendUrl.length() - 1)
                : resetFrontendUrl;
        return base + "#token=" + jti;
    }

    private String rateLimitKey(String username) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(username.getBytes(StandardCharsets.UTF_8));
            final StringBuilder result = new StringBuilder("password-reset:");
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Password reset rate limiting is unavailable", exception);
        }
    }
}
