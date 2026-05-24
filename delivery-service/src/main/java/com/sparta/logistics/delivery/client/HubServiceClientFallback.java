package com.sparta.logistics.delivery.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class HubServiceClientFallback implements HubServiceClient {

    @Override
    public void checkHubExists(UUID hubId) {
        log.warn("[HubServiceClient Fallback] Hub Service 응답 없음. hubId={}", hubId);
        throw new BusinessException(DeliveryErrorCode.HUB_SERVICE_UNAVAILABLE);
    }
}
