package com.saas.directory.configuration;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.saas.directory.model.TokenType;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final UserAuthenticationProvider userAuthenticationProvider;

    public JwtAuthFilter(UserAuthenticationProvider userAuthenticationProvider) {
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            final String jwtToken = header.substring(7);
            final TokenType requiredTokenType;

            // Exact path match: contains() also matched lookalikes such as /refresh-token-evil,
            // which would validate an access token as a refresh token (or vice versa).
            if ("/refresh-token".equals(request.getRequestURI())
                    && request.getMethod().equalsIgnoreCase(HttpMethod.POST.name())) {
                requiredTokenType = TokenType.AUTH_REFRESH;
            } else {
                requiredTokenType = TokenType.AUTH_ACCESS;
            }

            try {
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                userAuthenticationProvider.authenticateWithToken(jwtToken, requiredTokenType));
            } catch (IllegalAccessException e) {
                // Same contract as the ControllerAdvice handler for handler-level denials:
                // 401 with the static ControllerAdvice.INVALID_TOKEN_MESSAGE body, so one
                // failure has one shape regardless of the layer that rejects it.
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(ControllerAdvice.INVALID_TOKEN_MESSAGE);
                return;
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                throw e;
            }
        }
        filterChain.doFilter(request, response);
    }
}
