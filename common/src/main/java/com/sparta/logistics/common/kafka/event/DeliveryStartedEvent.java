package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 토픽: delivery.started
 * 발행: DeliveryService / 구독: HubService
 *
 * AI 발송 시한 확정 후 배송이 실제 출발함을 알림
 * HubService는 이를 수신하여 reserved 재고를 실제 차감함
 *
 * 파티션 키: deliveryId (배송 단위 순서 보장)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStartedEvent {
    private UUID eventId;
    private UUID deliveryId;
    private UUID orderId;
    private List<DeliveryOrderItemPayload> orderItems;
}
