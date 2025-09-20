package com.portcelana.natiart.configuration;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

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
                final AuthenticationResponseDto authenticationResponse = webClientBuilder.build()
                        .post()
                        .uri(directoryServiceUrl + "/validate-token")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(AuthenticationResponseDto.class)
                        .block();

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        authenticationResponse.getPrincipal(),
                        authenticationResponse.getAuthorities().stream()
                                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                                .toList()
                );

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