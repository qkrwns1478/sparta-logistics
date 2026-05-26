package com.sparta.logistics.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class HubServiceClientFallback implements HubServiceClient {

    @Override
    public void checkHubExists(UUID hubId) {
        log.warn("[Fallback] hub-service 연결 실패 - hubId 검증 건너뜀: {}", hubId);
    }
}
