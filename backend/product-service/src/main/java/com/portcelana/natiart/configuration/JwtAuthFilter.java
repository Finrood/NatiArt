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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(JwtAuthFilter.class);

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
                        .timeout(Duration.ofSeconds(5))
                        .block();

                // MUST use the three-arg constructor: the two-arg variant treats the second argument
                // as CREDENTIALS and builds an *unauthenticated* token with empty authorities, which
                // made @PreAuthorize deny every legitimate (and admin) request.
                final List<SimpleGrantedAuthority> grantedAuthorities =
                        authenticationResponse.getAuthorities().stream()
                                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                                .toList();
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        authenticationResponse.getPrincipal(),
                        authenticationResponse.getCredentials(),
                        grantedAuthorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (Exception e) {
                LOGGER.warn("Token validation is temporarily unavailable: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication service unavailable");
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