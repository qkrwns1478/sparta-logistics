package com.sparta.logistics.user.validator;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.client.CompanyServiceClient;
import com.sparta.logistics.user.client.HubServiceClient;
import com.sparta.logistics.user.exception.UserErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubCompanyValidator {

    private final HubServiceClient hubServiceClient;
    private final CompanyServiceClient companyServiceClient;

    public void validate(UUID hubId, UUID companyId) {
        if (hubId != null) {
            try {
                hubServiceClient.checkHubExists(hubId);
            } catch (FeignException.NotFound e) {
                throw new BusinessException(UserErrorCode.HUB_NOT_FOUND);
            } catch (FeignException e) {
                throw new BusinessException(UserErrorCode.HUB_SERVICE_UNAVAILABLE);
            }
        }
        if (companyId != null) {
            try {
                companyServiceClient.checkCompanyExists(companyId);
            } catch (FeignException.NotFound e) {
                throw new BusinessException(UserErrorCode.COMPANY_NOT_FOUND);
            } catch (FeignException e) {
                throw new BusinessException(UserErrorCode.COMPANY_SERVICE_UNAVAILABLE);
            }
        }
    }
}
