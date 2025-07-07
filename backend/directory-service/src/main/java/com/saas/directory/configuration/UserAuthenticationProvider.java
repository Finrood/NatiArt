package com.saas.directory.configuration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.dto.UserDto;
import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import com.saas.directory.repository.TokenRepository;
import com.saas.directory.service.UserManager;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;


@Component
public class UserAuthenticationProvider {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthenticationProvider.class);
    private final TokenRepository tokenRepository;
    private final UserManager userManager;
    @Value("${saas.security.jwt.key.secret}")
    private String secretKey;
    @Value("${saas.security.jwt.expiration}")
    private Long accessTokenExpiration;
    @Value("${saas.security.jwt.refresh.expiration}")
    private Long refreshTokenExpiration;

    public UserAuthenticationProvider(TokenRepository tokenRepository, UserManager userManager) {
        this.tokenRepository = tokenRepository;
        this.userManager = userManager;
    }

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    @Transactional
    public String createAccessToken(UserDto userDto) {
        final Instant now = Instant.now();
        final Instant validUntil = now.plus(accessTokenExpiration, ChronoUnit.MILLIS);

        final String token = JWT.create()
                .withIssuer(userDto.getUsername())
                .withIssuedAt(now)
                .withClaim("id", userDto.getId())
                .withClaim("username", userDto.getUsername())
                .withClaim("roles", userDto.getRole().name())
                .withExpiresAt(validUntil)
                .sign(Algorithm.HMAC256(secretKey));
        final User user = userManager.getUserOrDie(userDto.getUsername());
        final Token savedToken = tokenRepository.save(new Token(token, user, TokenType.AUTH_ACCESS, validUntil));
        return savedToken.getToken();
    }

    @Transactional
    public String createRefreshToken(UserDto userDto) {
        final Instant now = Instant.now();
        final Instant validUntil = now.plus(refreshTokenExpiration, ChronoUnit.MILLIS);

        final String token = JWT.create()
                .withIssuer(userDto.getUsername())
                .withIssuedAt(now)
                .withExpiresAt(validUntil)
                .sign(Algorithm.HMAC256(secretKey));

        final User user = userManager.getUserOrDie(userDto.getUsername());
        final Token savedToken = tokenRepository.save(new Token(token, user, TokenType.AUTH_REFRESH, validUntil));
        return savedToken.getToken();
    }

    @Transactional
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException, IllegalAccessException {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null) {
            final String[] authElements = header.split(" ");

            if (authElements.length == 2 && "Bearer".equals(authElements[0])) {
                final String refreshToken = authElements[1];
                final Authentication currentAuth = authenticateWithToken(refreshToken, TokenType.AUTH_REFRESH);
                if (currentAuth != null) {
                    final UserDto userDto = (UserDto) currentAuth.getPrincipal();
                    final String accessToken = createAccessToken(userDto);
                    final UserAuthDto userAuthDto = new UserAuthDto(accessToken, refreshToken);
                    new ObjectMapper().writeValue(response.getOutputStream(), userAuthDto);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Authentication authenticateWithToken(String token, TokenType tokenType) throws IllegalAccessException {
        final Optional<Token> dbToken = tokenRepository.findByTokenAndTokenType(token, tokenType);
        final boolean isTokenValid = dbToken
                .map(t -> !t.isExpired())
                .orElse(false);
        if (!isTokenValid) {
            throw new IllegalAccessException(String.format("Authentication Token [%s] is not valid", token));
        }

        final DecodedJWT decodedJWT = decodeJWT(token);

        if (!dbToken.get().getUser().getUsername().equals(decodedJWT.getIssuer())) {
            invalidateToken(token);
            throw new IllegalAccessException(String.format("User [%s] is not the authentication token issuer for token [%s]", decodedJWT.getIssuer(), token));
        }

        final String role = decodedJWT.getClaim("roles").asString();
        final GrantedAuthority authority;
        if (role != null) {
            authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
        } else {
            authority = new SimpleGrantedAuthority("ROLE_" + dbToken.get().getUser().getRole().getLabel());
        }

        return new UsernamePasswordAuthenticationToken(UserDto.from(dbToken.get().getUser(), null), null, Collections.singletonList(authority));
    }

    @Transactional
    public void invalidateToken(String token) {
        tokenRepository.deleteByToken(token);
    }

    public String extractEmailClaim(String token) {
        try {
            return decodeJWT(token).getClaim("email").asString();
        } catch (JWTVerificationException exception) {
            logger.error("Error verifying JWT token: {}", exception.getMessage());
            return null;
        }
    }

    public String extractIdClaim(String token) {
        try {
            return decodeJWT(token).getClaim("id").asString();
        } catch (JWTVerificationException exception) {
            logger.error("Error verifying JWT token: {}", exception.getMessage());
            return null;
        }
    }

    private DecodedJWT decodeJWT(String token) {
        final Algorithm algorithm = Algorithm.HMAC256(secretKey);
        final JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }
}
