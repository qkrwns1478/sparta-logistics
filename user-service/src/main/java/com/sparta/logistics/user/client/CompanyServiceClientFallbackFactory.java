package com.sparta.logistics.user.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.exception.UserErrorCode;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class CompanyServiceClientFallbackFactory
        implements FallbackFactory<CompanyServiceClient> {

    @Override
    public CompanyServiceClient create(Throwable cause) {

        return new CompanyServiceClient() {

            @Override
            public void checkCompanyExists(UUID companyId) {

                // 404는 원래 예외 그대로 전달
                if (cause instanceof FeignException.NotFound) {
                    throw (FeignException.NotFound) cause;
                }

                log.error("[FallbackFactory] company-service 호출 실패", cause);

                throw new BusinessException(
                        UserErrorCode.COMPANY_SERVICE_UNAVAILABLE
                );
            }
        };
    }
}
