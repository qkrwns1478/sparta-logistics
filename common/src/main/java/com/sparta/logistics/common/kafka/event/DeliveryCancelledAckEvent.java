package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: delivery.cancelled.ack
 * 발행: DeliveryService / 구독: OrderService (Orchestrator)
 * <p>
 * 주문 취소 Orchestration Saga Step 3-2 / 3-3
 * DeliveryService가 배송 취소를 완료하고 응답함 (Step 3-2)
 * OrderService Orchestrator는 이를 수신한 뒤 restore.stock.command를 발행함 (Step 3-3)
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCancelledAckEvent {
    private UUID eventId;
    private UUID deliveryId;
    private UUID orderId;
}
