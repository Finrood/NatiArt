package com.portcelana.natiart.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	private final CorsConfigurationSource corsFilter;

	public SecurityConfig(CorsConfigurationSource corsFilter) {
		this.corsFilter = corsFilter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new JwtAuthFilter(), BasicAuthenticationFilter.class)
				.cors(cors -> cors.configurationSource(corsFilter))
				.authorizeHttpRequests(request -> request
						.anyRequest().authenticated());

		return http.build();
	}
}
