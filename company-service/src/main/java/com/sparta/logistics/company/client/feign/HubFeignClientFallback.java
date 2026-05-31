package com.sparta.logistics.company.client.feign;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.client.model.HubResponse;
import com.sparta.logistics.company.exception.CompanyErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Hub Service 장애 시 Fallback 처리
 *
 * - checkHubExists:
 *   서비스 호출 실패(타임아웃/연결 오류 등) 시 fallback 발생
 *   → HUB_SERVICE_UNAVAILABLE 처리
 *   → 참고: 404(HUB_NOT_FOUND)는 fallback이 아니라 정상 응답 처리
 *
 * - getHub / getHubsByIds:
 *   장애 시 "알 수 없음"으로 degrade(성능/기능 축소 운영) 처리
 */
@Slf4j
@Component
public class HubFeignClientFallback implements HubFeignClient {

    @Override
    public void checkHubExists(UUID hubId) {
        log.warn("[HubFeignClient Fallback] Hub Service 장애. hubId={}", hubId);
        throw new BusinessException(CompanyErrorCode.EXTERNAL_HUB_SERVICE_UNAVAILABLE);
    }

    @Override
    public HubResponse getHub(UUID hubId) {
        log.warn("[HubFeignClient Fallback] Hub Service 응답 없음. hubId={}", hubId);
        return new HubResponse(hubId, "알 수 없음");
    }

    @Override
    public List<HubResponse> getHubsByIds(List<UUID> hubIds) {
        log.warn("[HubFeignClient Fallback] Hub Service 응답 없음. hubIds={}", hubIds);
        return hubIds.stream()
                .map(id -> new HubResponse(id, "알 수 없음"))
                .toList();
    }
}
