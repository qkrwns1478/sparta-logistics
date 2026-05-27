package com.sparta.logistics.common.config;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Auditing을 위한 등록자/수정자(Auditor) 자동 추적 설정 클래스
 *
 * <p>X-User-Id 헤더가 없는 비인증 요청(예: 회원가입)의 경우 SYSTEM_AUDITOR UUID를 반환하여
 * BaseEntity의 createdBy / updatedBy NOT NULL 제약조건 위반을 방지합니다.</p>
 */
@EnableJpaAuditing
@Configuration
public class AuditorAwareConfig {

    /**
     * 비인증 요청(회원가입 등)에서 Auditor를 식별할 수 없을 때 사용하는 시스템 UUID.
     * DB에 저장될 값이므로 전 서비스에서 동일한 값을 유지해야 합니다.
     */
    public static final UUID SYSTEM_AUDITOR = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userId = request.getHeader("X-User-Id");

                if (StringUtils.hasText(userId)) {
                    try {
                        return Optional.of(UUID.fromString(userId));
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(CommonErrorCode.INVALID_HEADER_FORMAT);
                    }
                }
            }

            // X-User-Id 헤더 없음(비인증 요청): SYSTEM_AUDITOR로 대체
            return Optional.of(SYSTEM_AUDITOR);
        };
    }
}
