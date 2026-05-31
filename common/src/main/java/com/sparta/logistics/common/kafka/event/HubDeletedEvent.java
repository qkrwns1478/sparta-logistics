package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: hub.deleted
 * 발행: HubService / 구독: DeliveryService
 *
 * 허브 삭제 시 소속 배송 담당자 soft delete cascade 처리
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HubDeletedEvent {
    private UUID eventId;
    private UUID hubId;
    private UUID deletedBy;
}
