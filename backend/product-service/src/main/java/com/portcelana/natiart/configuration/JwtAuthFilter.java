package com.portcelana.natiart.configuration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.portcelana.natiart.dto.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.client.WebClient;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final WebClient.Builder webClientBuilder;
    private final String directoryServiceUrl;

    public JwtAuthFilter(WebClient.Builder webClientBuilder, @Value("${directory.service.url}") String directoryServiceUrl) {
        this.webClientBuilder = webClientBuilder;
        this.directoryServiceUrl = directoryServiceUrl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String token = extractToken(request);
        if (token != null) {
            try {
                final Authentication authentication = webClientBuilder.build()
                        .post()
                        .uri(directoryServiceUrl + "/validate-token")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Authentication.class)
                        .block();

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        final String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}