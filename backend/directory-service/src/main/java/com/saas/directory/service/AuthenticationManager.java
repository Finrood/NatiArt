package com.saas.directory.service;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.dto.UserDto;
import com.saas.directory.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationManager {
    private final UserManager userManager;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthenticationProvider userAuthenticationProvider;
    private final TokenManager tokenManager;

    public AuthenticationManager(UserManager userManager,
                                 PasswordEncoder passwordEncoder,
                                 UserAuthenticationProvider userAuthenticationProvider,
                                 TokenManager tokenManager) {
        this.userManager = userManager;
        this.passwordEncoder = passwordEncoder;
        this.userAuthenticationProvider = userAuthenticationProvider;
        this.tokenManager = tokenManager;
    }

    @Transactional
    public UserAuthDto login(CredentialsDto credentialsDto) {
        final User user = userManager.getUser(credentialsDto.username())
                .filter(u -> passwordEncoder.matches(credentialsDto.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid credentials", HttpStatus.UNAUTHORIZED));
        final UserDto userDto = UserDto.from(user, null);
        return new UserAuthDto(userAuthenticationProvider.createAccessToken(userDto), userAuthenticationProvider.createRefreshToken(userDto));
    }

    @Transactional
    public void logout(HttpServletRequest request, String username) {
        request.getSession().invalidate();

        if (username != null && !username.isEmpty()) {
            tokenManager.clearTokensOfUser(userManager.getUserOrDie(username));
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            final String jwtToken = authorizationHeader.substring(7);
            userAuthenticationProvider.invalidateToken(jwtToken);
        }
    }
}
