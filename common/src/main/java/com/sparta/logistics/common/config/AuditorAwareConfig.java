package com.sparta.logistics.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@EnableJpaAuditing
@Configuration
public class AuditorAwareConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()
                    || authentication.getName().equals("anonymousUser")) {
                return Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            }

            return Optional.of(UUID.fromString(authentication.getName()));
        };
    }
}
