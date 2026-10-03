package com.portcelana.natiart.configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditConfig {
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public DateTimeProvider utcDateTimeProvider(Clock utcClock) {
        return () -> Optional.of(Instant.now(utcClock));
    }
}
