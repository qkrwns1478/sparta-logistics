package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: delivery.cancellation.failed
 * 발행: DeliveryService / 구독: OrderService (Orchestrator)
 *
 * 주문 취소 Orchestration Saga 보상 Step 4-1
 * 배송이 이미 이동 중이어서 취소 불가한 경우 발행함
 * OrderService Orchestrator는 이를 수신하여 주문 상태를 CANCELLING → 이전 상태로 복구함
 *
 * 파티션 키: orderId
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCancellationFailedEvent {
    private UUID eventId;
    private UUID orderId;
    private UUID deliveryId;
    private String reason;
}
