package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: delivery.created
 * 발행: DeliveryService / 구독: OrderService, SlackService(NotificationService)
 * <p>
 * 배송 생성이 완료되면 발행함
 * - OrderService: 주문 상태를 ACCEPTED로 갱신, deliveryId 저장
 * - SlackService: AI 발송 시한 계산 후 슬랙 알림 발송
 * <p>
 * 파티션 키: deliveryId (배송 단위 이벤트 순서 보장)
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCreatedEvent {
    private UUID eventId;
    private UUID deliveryId;
    private UUID orderId;
    private UUID sourceHubId;
    private UUID destinationHubId;
    // 담당 배송자 ID (배정 전이면 null)
    private UUID companyDeliveryManagerId;
}
