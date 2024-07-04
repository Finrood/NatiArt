package com.saas.directory.service;

import com.saas.directory.dto.ResetPasswordDto;
import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PasswordManager {
	private final UserManager userManager;
	private final UserRepository userRepository;
	private final TokenManager tokenManager;
	private final PasswordEncoder passwordEncoder;

	public PasswordManager(UserManager userManager,
						   UserRepository userRepository,
						   TokenManager tokenManager,
                           PasswordEncoder passwordEncoder) {
		this.userManager = userManager;
		this.userRepository = userRepository;
		this.tokenManager = tokenManager;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public Token getPasswordResetTokenOrDie(String token) {
		return tokenManager.getTokenByTokenAndTokenTypeOrDie(token, TokenType.PASSWORD_RESET);
	}

	@Transactional(readOnly = true)
	public Token getValidPasswordResetTokenOrDie(String token) {
		return tokenManager.getValidTokenTokenAndTokenTypeOrDie(token, TokenType.PASSWORD_RESET);
	}

	//TODO CALL NOTIFICATION SERVICE TO SEND THE RESET PASSWORD EMAIL
	public void notifyResetPassword(String username) {
		//passwordResetService.notifyResetPassword(username);
	}

	@Transactional
	public void doResetPassword(String token, ResetPasswordDto resetPasswordDto) {
		if (! resetPasswordDto.password().equals(resetPasswordDto.passwordConfirmation())) {
			throw new IllegalArgumentException("Passwords must be identical");
		}
		if (! StringUtils.hasText(resetPasswordDto.password())) {
			throw new IllegalArgumentException("Password cannot be empty");
		}

		final Token passwordResetToken = getPasswordResetTokenOrDie(token);

		final User user = passwordResetToken.getUser();

		user.setPasswordHash(passwordEncoder.encode(resetPasswordDto.password()));
		userRepository.save(user);
		tokenManager.clearTokensOfUser(user);
	}
}
