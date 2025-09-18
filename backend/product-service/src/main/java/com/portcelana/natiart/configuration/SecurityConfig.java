package com.portcelana.natiart.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final CorsConfigurationSource corsFilter;
    private final WebClient.Builder webClientBuilder;
    private final String directoryServiceUrl;

    public SecurityConfig(CorsConfigurationSource corsFilter, WebClient.Builder webClientBuilder, @Value("${directory.service.url}") String directoryServiceUrl) {
        this.corsFilter = corsFilter;
        this.webClientBuilder = webClientBuilder;
        this.directoryServiceUrl = directoryServiceUrl;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new JwtAuthFilter(webClientBuilder, directoryServiceUrl), BasicAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsFilter))
                .authorizeHttpRequests(request -> request
                        .anyRequest().permitAll());

        return http.build();
    }
}
