package com.sparta.logistics.company.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository 테스트에서 JPA Auditing(@CreatedDate, @CreatedBy) 동작을 위한 테스트 전용 설정
 * @DataJpaTest 환경에서는 Auditing Bean이 자동 등록되지 않아
 * created_at, created_by 값이 null로 저장되는 문제를 방지하기 위해 사용
 */
@TestConfiguration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.of(UUID.randomUUID());
    }
}
