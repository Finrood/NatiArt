package com.saas.directory.configuration;

import com.saas.directory.model.TokenType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final UserAuthenticationProvider userAuthenticationProvider;

    public JwtAuthFilter(UserAuthenticationProvider userAuthenticationProvider) {
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            final String jwtToken = header.substring(7);
            final TokenType requiredTokenType;

            if (request.getRequestURI().contains("/refresh-token") && request.getMethod().equalsIgnoreCase(HttpMethod.POST.name())) {
                requiredTokenType = TokenType.AUTH_REFRESH;
            } else {
                requiredTokenType = TokenType.AUTH_ACCESS;
            }

            try {
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(userAuthenticationProvider.authenticateWithToken(jwtToken, requiredTokenType));
            } catch (IllegalAccessException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                throw e;
            }
        }
        filterChain.doFilter(request, response);
    }
}
