package com.pfe.sageline.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@Slf4j
public class JpaAuditingConfig {

    private final SecurityUtils securityUtils;

    public JpaAuditingConfig(SecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
    }

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> {
            try {
                return Optional.ofNullable(securityUtils.getCurrentUserId());
            } catch (Exception e) {
                log.debug("No auth context for auditor lookup: {}", e.getMessage());
                return Optional.empty();
            }
        };
    }
}
