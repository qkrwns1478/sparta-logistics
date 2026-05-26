package com.sparta.logistics.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class CompanyServiceClientFallback implements CompanyServiceClient {

    @Override
    public void checkCompanyExists(UUID companyId) {
        log.warn("[Fallback] company-service 연결 실패 - companyId 검증 건너뜀: {}", companyId);
    }
}
