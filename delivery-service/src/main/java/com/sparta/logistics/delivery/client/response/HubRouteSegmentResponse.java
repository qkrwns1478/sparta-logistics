package com.sparta.logistics.delivery.client.response;

import java.math.BigDecimal;
import java.util.UUID;

// hub-service 팀과 실제 응답 필드 구조 협의 필요
// GET /api/v1/hub-routes?sourceHubId=&destinationHubId= 응답의 각 구간 항목
public record HubRouteSegmentResponse(
        int sequence,
        boolean lastMile,           // 마지막 구간(목적지 허브→업체) 여부
        UUID sourceHubId,
        UUID destinationHubId,      // lastMile=true 이면 null 가능
        BigDecimal estimatedDistance,
        int estimatedDuration       // 예상 소요 시간 (분)
) {}
